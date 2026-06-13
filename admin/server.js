"use strict";

const express = require("express");
const path = require("path");
const fs = require("fs");
const bcrypt = require("bcryptjs");
const admin = require("firebase-admin");

const KEY_PATH = path.join(__dirname, "serviceAccountKey.json");
if (!fs.existsSync(KEY_PATH)) {
    console.error(
        "\n  ✗ Missing admin/serviceAccountKey.json\n\n" +
        "  Get it once: Firebase Console → Project settings → Service accounts →\n" +
        "  Generate new private key → save the file as admin/serviceAccountKey.json\n",
    );
    process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(KEY_PATH)) });
const db = admin.firestore();

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

const USERNAME_RE = /^[a-z0-9_]{3,20}$/;
const wrap = (fn) => (req, res) =>
    fn(req, res).catch((e) => res.status(500).json({ error: String(e && e.message || e) }));

// --- Users ---------------------------------------------------------------

app.get("/api/users", wrap(async (_req, res) => {
    const snap = await db.collection("usernames").get();
    const users = await Promise.all(
        snap.docs.map(async (doc) => {
            const d = doc.data();
            let displayName = "";
            let avatarUrl = "";
            if (d.uid) {
                const u = (await db.doc(`users/${d.uid}`).get()).data() || {};
                displayName = u.displayName || "";
                avatarUrl = u.avatarUrl || "";
            }
            return { username: doc.id, uid: d.uid || "", displayName, avatarUrl, createdAt: d.createdAt || 0 };
        }),
    );
    users.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    res.json(users);
}));

app.post("/api/reset-password", wrap(async (req, res) => {
    const username = String(req.body.username || "").trim().toLowerCase();
    const password = String(req.body.password || "");
    if (!USERNAME_RE.test(username)) return res.status(400).json({ error: "Некорректное имя" });
    if (password.length < 6) return res.status(400).json({ error: "Пароль минимум 6 символов" });
    const ref = db.doc(`usernames/${username}`);
    if (!(await ref.get()).exists) return res.status(404).json({ error: "Пользователь не найден" });
    const passwordHash = await bcrypt.hash(password, 10);
    await ref.update({ passwordHash });
    res.json({ ok: true });
}));

app.post("/api/delete-user", wrap(async (req, res) => {
    const username = String(req.body.username || "").trim().toLowerCase();
    const ref = db.doc(`usernames/${username}`);
    const snap = await ref.get();
    if (!snap.exists) return res.status(404).json({ error: "Пользователь не найден" });
    const uid = snap.data().uid;
    if (uid) {
        // Deleting connections triggers the onConnectionDeleted cleanup function.
        const conns = await db.collection("connections").where("members", "array-contains", uid).get();
        await Promise.all(conns.docs.map((d) => d.ref.delete()));
        await db.doc(`users/${uid}`).delete().catch(() => {});
    }
    await ref.delete();
    res.json({ ok: true });
}));

// --- Crash reports -------------------------------------------------------

app.get("/api/crashes", wrap(async (_req, res) => {
    const snap = await db.collection("crashReports").orderBy("createdAt", "desc").limit(100).get();
    res.json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
}));

app.post("/api/delete-crash", wrap(async (req, res) => {
    await db.doc(`crashReports/${String(req.body.id)}`).delete();
    res.json({ ok: true });
}));

app.post("/api/clear-crashes", wrap(async (_req, res) => {
    const snap = await db.collection("crashReports").limit(400).get();
    await Promise.all(snap.docs.map((d) => d.ref.delete()));
    res.json({ ok: true, deleted: snap.size });
}));

const PORT = process.env.PORT || 4000;
// Bind to loopback only — the panel is intended for local use on your machine.
app.listen(PORT, "127.0.0.1", () => {
    console.log(`\n  Delirium admin panel → http://127.0.0.1:${PORT}\n`);
});
