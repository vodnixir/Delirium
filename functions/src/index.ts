import { onDocumentCreated, onDocumentDeleted, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import * as bcrypt from "bcryptjs";

const DAY_MS = 24 * 60 * 60 * 1000;

initializeApp();

// Region must match the Firestore database region (chosen in console).
// We set europe-west3 here; change if the user picked a different region.
const REGION = "europe-west3";

interface PhotoDoc {
    connectionId?: string;
    senderId?: string;
    storageUrl?: string;
    caption?: string;
    mediaType?: string;
    thumbnailUrl?: string;
}

interface ConnectionDoc {
    members?: string[];
    names?: Record<string, string>;
    streakPostDays?: Record<string, number>;
    streakCount?: number;
    streakDay?: number;
}

interface UserDoc {
    fcmToken?: string;
}

export const onPhotoCreated = onDocumentCreated(
    {
        document: "photos/{photoId}",
        region: REGION,
    },
    async (event) => {
        const snap = event.data;
        if (!snap) {
            logger.warn("onPhotoCreated fired with no snapshot");
            return;
        }
        const photoId = event.params.photoId;
        const photo = snap.data() as PhotoDoc;
        const { connectionId, senderId, storageUrl } = photo;

        if (!connectionId || !senderId || !storageUrl) {
            logger.warn("Photo doc missing required fields", { photoId, photo });
            return;
        }

        // The widget renders a bitmap, so for video posts push the still thumbnail
        // (an image) rather than the .mp4 URL.
        const widgetPhotoUrl = photo.thumbnailUrl ?? storageUrl;

        const db = getFirestore();
        const connectionSnap = await db.doc(`connections/${connectionId}`).get();
        const connection = connectionSnap.data() as ConnectionDoc | undefined;
        const members = connection?.members ?? [];
        const senderName = connection?.names?.[senderId] ?? "Друг";

        // Per-friend Snapstreak: counts days where both members posted.
        await updateStreak(db, connectionId, members, senderId);

        const recipients = members.filter((uid) => uid !== senderId);

        if (recipients.length === 0) {
            logger.info("No recipients (no other members yet), skipping FCM", {
                photoId,
                connectionId,
            });
            return;
        }

        const messaging = getMessaging();

        await Promise.all(
            recipients.map(async (recipientUid) => {
                const userSnap = await db.doc(`users/${recipientUid}`).get();
                const user = userSnap.data() as UserDoc | undefined;
                const token = user?.fcmToken;

                if (!token) {
                    logger.info("Recipient has no FCM token; skipping", {
                        recipientUid,
                    });
                    return;
                }

                try {
                    await messaging.send({
                        token,
                        data: {
                            photoId,
                            photoUrl: widgetPhotoUrl,
                            connectionId,
                            senderId,
                            senderName,
                            createdAt: String(Date.now()),
                        },
                        android: {
                            priority: "high",
                        },
                    });
                    logger.info("FCM sent", { recipientUid, photoId });
                } catch (err) {
                    logger.error("FCM send failed", { recipientUid, err });
                }
            }),
        );
    },
);

/**
 * Maintains the per-connection Snapstreak. The streak grows by one the first
 * time, on a given day, that BOTH members have posted; it implicitly resets
 * when a day is missed (clients treat a streakDay older than yesterday as 0).
 */
async function updateStreak(
    db: ReturnType<typeof getFirestore>,
    connectionId: string,
    members: string[],
    senderId: string,
): Promise<void> {
    if (members.length < 2) return;
    const today = Math.floor(Date.now() / DAY_MS);
    const connRef = db.doc(`connections/${connectionId}`);
    try {
        await db.runTransaction(async (tx) => {
            const snap = await tx.get(connRef);
            const data = snap.data() as ConnectionDoc | undefined;
            if (!data) return;
            const postDays: Record<string, number> = { ...(data.streakPostDays ?? {}) };
            postDays[senderId] = today;
            let streakCount = data.streakCount ?? 0;
            let streakDay = data.streakDay ?? 0;
            const bothToday = members.every((m) => postDays[m] === today);
            if (bothToday && streakDay !== today) {
                streakCount = streakDay === today - 1 ? streakCount + 1 : 1;
                streakDay = today;
            }
            tx.update(connRef, { streakPostDays: postDays, streakCount, streakDay });
        });
    } catch (err) {
        logger.error("updateStreak failed", { connectionId, err });
    }
}

/**
 * When a connection is deleted (a user removed a friend) wipe its photos and
 * their Storage files so nothing is left behind.
 */
export const onConnectionDeleted = onDocumentDeleted(
    { document: "connections/{connectionId}", region: REGION },
    async (event) => {
        const connectionId = event.params.connectionId;
        const db = getFirestore();
        try {
            const photos = await db
                .collection("photos")
                .where("connectionId", "==", connectionId)
                .get();
            await Promise.all(photos.docs.map((d) => db.recursiveDelete(d.ref)));
            logger.info("Deleted photos for connection", { connectionId, count: photos.size });
        } catch (err) {
            logger.error("Failed deleting photo docs", { connectionId, err });
        }
        try {
            await getStorage().bucket().deleteFiles({ prefix: `photos/${connectionId}/` });
        } catch (err) {
            logger.error("Failed deleting storage files", { connectionId, err });
        }
    },
);

/**
 * Denormalizes a photo's reactions onto the photo doc as `reactionEmojis` (one
 * entry per reacting member) so feeds/grids can show them without reading the
 * subcollection per thumbnail.
 */
export const onReactionWritten = onDocumentWritten(
    { document: "photos/{photoId}/reactions/{userId}", region: REGION },
    async (event) => {
        const photoId = event.params.photoId;
        const db = getFirestore();
        try {
            const snap = await db.collection(`photos/${photoId}/reactions`).get();
            const emojis = snap.docs
                .map((d) => d.data().emoji as string | undefined)
                .filter((e): e is string => !!e);
            await db.doc(`photos/${photoId}`).set({ reactionEmojis: emojis }, { merge: true });
        } catch (err) {
            logger.error("onReactionWritten failed", { photoId, err });
        }
    },
);

/**
 * Denormalizes photo views into `photos/{photoId}.seenBy` so the sender can see
 * "seen" status without reading the views subcollection.
 */
export const onViewCreated = onDocumentCreated(
    { document: "photos/{photoId}/views/{userId}", region: REGION },
    async (event) => {
        const { photoId, userId } = event.params;
        try {
            await getFirestore()
                .doc(`photos/${photoId}`)
                .set({ seenBy: FieldValue.arrayUnion(userId) }, { merge: true });
        } catch (err) {
            logger.error("onViewCreated failed", { photoId, userId, err });
        }
    },
);

// ---------------------------------------------------------------------------
// Username / password auth via custom tokens.
//
// Passwords never reach Firebase Auth: we store a bcrypt hash in Firestore
// under usernames/{username} (locked to clients by security rules) and mint a
// custom token for a stable uid. Clients then call signInWithCustomToken().
//
// NOTE: createCustomToken() needs the function's runtime service account to
// have the "Service Account Token Creator" role (roles/iam.serviceAccountTokenCreator).
// Grant it once after the first deploy or token minting will fail.
// ---------------------------------------------------------------------------

const USERNAME_RE = /^[a-z0-9_]{3,20}$/;
const MIN_PASSWORD = 6;
const BCRYPT_ROUNDS = 10;

interface UsernameDoc {
    uid?: string;
    passwordHash?: string;
}

function normalizeUsername(raw: unknown): string {
    return String(raw ?? "").trim().toLowerCase();
}

/**
 * Mints a Firebase custom token, translating the opaque signing failure that
 * happens when the runtime service account lacks the "Service Account Token
 * Creator" role into a clear, actionable error instead of a raw INTERNAL.
 */
async function mintToken(uid: string, username: string): Promise<string> {
    try {
        return await getAuth().createCustomToken(uid, { username });
    } catch (err) {
        logger.error("createCustomToken failed", { uid, err });
        throw new HttpsError(
            "internal",
            "Не удалось выпустить токен входа. Сервис-аккаунту функций нужна роль " +
                "«Service Account Token Creator» (roles/iam.serviceAccountTokenCreator).",
        );
    }
}

export const registerUser = onCall({ region: REGION }, async (request) => {
    const username = normalizeUsername(request.data?.username);
    const password = String(request.data?.password ?? "");
    const displayNameRaw = String(request.data?.displayName ?? "").trim();
    const displayName = displayNameRaw || String(request.data?.username ?? "").trim();

    if (!USERNAME_RE.test(username)) {
        throw new HttpsError(
            "invalid-argument",
            "Имя пользователя: 3–20 символов (латиница, цифры, _).",
        );
    }
    if (password.length < MIN_PASSWORD) {
        throw new HttpsError("invalid-argument", "Пароль не короче 6 символов.");
    }
    if (displayName.length === 0) {
        throw new HttpsError("invalid-argument", "Укажите отображаемое имя.");
    }

    const db = getFirestore();
    const usernameRef = db.doc(`usernames/${username}`);
    const newUid = db.collection("users").doc().id;
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);

    // Mint the token BEFORE persisting anything. If signing fails we abort with
    // a clear error and leave no orphaned "taken" username behind (the bug that
    // stranded accounts while the Token Creator role was missing).
    const token = await mintToken(newUid, username);

    await db.runTransaction(async (tx) => {
        const existing = await tx.get(usernameRef);
        if (existing.exists) {
            throw new HttpsError("already-exists", "Это имя уже занято.");
        }
        tx.set(usernameRef, {
            uid: newUid,
            passwordHash,
            createdAt: Date.now(),
        });
        tx.set(db.doc(`users/${newUid}`), {
            username,
            displayName,
            createdAt: Date.now(),
        });
    });

    return { token };
});

export const loginUser = onCall({ region: REGION }, async (request) => {
    const username = normalizeUsername(request.data?.username);
    const password = String(request.data?.password ?? "");

    if (username.length === 0 || password.length === 0) {
        throw new HttpsError("invalid-argument", "Введите имя и пароль.");
    }

    const db = getFirestore();
    const snap = await db.doc(`usernames/${username}`).get();
    const data = snap.data() as UsernameDoc | undefined;

    if (!data?.uid || !data.passwordHash) {
        throw new HttpsError("not-found", "Пользователь не найден.");
    }
    const ok = await bcrypt.compare(password, data.passwordHash);
    if (!ok) {
        throw new HttpsError("permission-denied", "Неверный пароль.");
    }

    const token = await mintToken(data.uid, username);
    return { token };
});
