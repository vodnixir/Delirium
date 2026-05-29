import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

initializeApp();

// Region must match the Firestore database region (chosen in console).
// We set europe-west3 here; change if the user picked a different region.
const REGION = "europe-west3";

interface PhotoDoc {
    connectionId?: string;
    senderId?: string;
    storageUrl?: string;
    caption?: string;
}

interface ConnectionDoc {
    members?: string[];
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

        const db = getFirestore();
        const connectionSnap = await db.doc(`connections/${connectionId}`).get();
        const connection = connectionSnap.data() as ConnectionDoc | undefined;
        const members = connection?.members ?? [];
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
                            photoUrl: storageUrl,
                            connectionId,
                            senderId,
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
