# Firestore & Storage rules

These files contain the access rules for Cloud Firestore and Cloud Storage.

## How to deploy

Once you have the Firebase CLI installed (`npm i -g firebase-tools`):

```sh
firebase login
firebase use delirium-e1a8f
firebase deploy --only firestore:rules
firebase deploy --only storage
```

Or paste the contents of each file directly into the Firebase Console:
- Firestore Database → Rules → paste `firestore.rules` → Publish
- Storage → Rules → paste `storage.rules` → Publish

## Notes

- `storage.rules` will fail to deploy until Cloud Storage is enabled (which
  requires upgrading the Firebase project to the Blaze plan).
- `firestore.rules` enforces couple membership via `get(...)`, which counts
  toward Firestore reads. For two users, this is negligible.
