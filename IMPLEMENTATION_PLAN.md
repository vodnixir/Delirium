# Delirium — план доработок (многопользовательские друзья, виджеты, галерея, рисование)

> Рабочий документ для продолжения между сессиями. Обновляй чек-лист статусов по мере выполнения.
> Утверждён пользователем 2026-05-28.

## Контекст и ограничения
- Сборку/запуск в среде ассистента сделать нельзя (нет gradle wrapper и Android SDK). Финальная сборка и проверка UI — в Android Studio.
- Коммитов и реальных данных в репо нет → схему Firestore меняем свободно, без миграции.
- Решения по развилкам (выбраны пользователем):
  - Обновление виджетов: **FCM-триггер + WorkManager fallback** (оба).
  - Навигация: **список друзей → лента конкретного друга**.
  - Рисование: **холст + возможность рисовать поверх фото**.

## Целевая архитектура

### Модель данных (Firestore)
- `users/{uid}` → `{ displayName, fcmToken }` (убрать единый `coupleId`).
- `connections/{connectionId}` → `{ members:[uidA,uidB], names:{uid→имя}, createdAt, lastPhotoAt }`.
- `photos/{photoId}` → `{ connectionId, senderId, storageUrl, caption?, createdAt }`.
- `invites/{code}` → `{ connectionId, createdBy, createdAt, expiresAt, used }`.
- Свои связи: `connections.whereArrayContains("members", uid)` (real-time listener).
- Имя пользователя спрашивается один раз (DataStore + users doc), пишется в `connection.names[uid]`.

### Виджет (per-instance)
- `WidgetConfigActivity` при размещении виджета → выбор друга → привязка `connectionId` к `GlanceId` (per-instance Glance state).
- Кэш фото пер-связь: `filesDir/widget/{connectionId}.jpg`.
- `WidgetUpdater.updateForConnection(connectionId, path)` обновляет только виджеты с этой связью.
- `SizeMode.Exact` + `fillMaxSize`/Crop, расширенные границы resize в `delirium_widget_info.xml`.

### Обновление виджетов
- `WidgetRefreshWorker` (CoroutineWorker): по привязанным связям тянет последнее фото из Firestore → скачивает → кэш → обновляет виджет.
- `WidgetWorkScheduler`: периодически (~30 мин) + разовый expedited после отправки фото и при ручном refresh.
- `DeliriumMessagingService`: читает `connectionId` из FCM payload, обновляет нужную связь.
- ⚠️ Бэкенд (отправитель FCM) вне репо: ему нужно добавить `connectionId` в data-payload рядом с `photoUrl`. До этого работает WorkManager-fallback.

### Галерея
- На ленте друга FAB-меню: Камера / Галерея / Рисовать.
- `PickVisualMedia` (системный Photo Picker, без доп. разрешений, ок для minSdk 29).
- `Uri → JPEG` со сжатием и поворотом через `androidx.exifinterface`.

### Рисование
- `DrawScreen` (Compose Canvas): рисование пальцем, палитра/толщина/ластик/undo/clear, опциональный фон-фото (камера или галерея). Экспорт Bitmap → JPEG → отправка как фото.

### Навигация
- Splash → Friends.
- Friends → AddFriend | Feed(connectionId).
- Feed → Camera(connectionId) | Draw(connectionId, backgroundUri?).
- Галерея — inline launcher на ленте (не отдельный route).

## Чек-лист реализации (статусы: TODO / WIP / DONE)

- [x] **Phase 0 — Модель данных** ✅ DONE
  - [x] `domain/model/Models.kt`: Connection, UserDoc(без coupleId), Photo(connectionId), Invite(connectionId), Friend(UI-модель)
  - [x] `data/couple/CoupleRepository.kt` → `data/connection/ConnectionRepository.kt` (rename + новые методы: observeMyFriends, createConnection(myName), joinByInvite(code,myName), getMyConnectionIds, getConnection)
  - [x] `data/photo/PhotoRepository.kt`: connectionId, latestPhoto, обновление lastPhotoAt
  - [x] `data/photo/PhotoCache.kt`: пер-связь файлы (`widget/{connectionId}.jpg`)
  - [x] `data/local/PreferencesRepository.kt`: хранить своё имя (myName)
  - [x] `data/AppContainer.kt`: connectionRepository
  - ⚠️ Консьюмеры старого API (SplashVM, FeedVM/Screen, CameraVM/Screen, PairingVM/Screen, AppNav) обновляются в Phase 1/5/6/Навигация — до этого проект не компилируется (промежуточное состояние).
- [x] **Phase 1 — Друзья** ✅ DONE
  - [x] `ui/friends/FriendsScreen.kt` + `FriendsViewModel.kt`
  - [x] адаптация Pairing → AddFriend (имя + код), возврат connectionId (`AddFriendScreen.kt` + `AddFriendViewModel.kt`)
- [x] **Phase 2 — Виджет per-friend** ✅ DONE
  - [x] `ui/widget/WidgetConfigActivity.kt` + `WidgetConfigScreen.kt` + `WidgetConfigViewModel.kt`
  - [x] `widget/DeliriumGlanceWidget.kt`: ConnectionIdKey, плейсхолдер (резолвит фото из PhotoCache по connectionId на рендере)
  - [x] `widget/WidgetUpdater.kt`: bindConnection / refreshConnection / refreshAll
  - [x] AndroidManifest: регистрация config activity (intent-filter APPWIDGET_CONFIGURE)
- [x] **Phase 3 — Масштабирование виджета** ✅ DONE
  - [x] `DeliriumGlanceWidget`: SizeMode.Exact
  - [x] `res/xml/delirium_widget_info.xml`: расширенные границы resize + `android:configure`
- [x] **Phase 4 — Обновления (FCM + WorkManager)** ✅ DONE
  - [x] `widget/WidgetPhotoSync.kt` (общий помощник: download → cache → refresh; syncConnection/syncFromUrl/syncAll)
  - [x] `widget/WidgetRefreshWorker.kt` (CoroutineWorker, по connectionId или все)
  - [x] `widget/WidgetWorkScheduler.kt` (periodic 30мин KEEP + expedited refreshNow)
  - [x] `messaging/DeliriumMessagingService.kt`: читает connectionId (+ fallback syncAll)
  - [x] `PreferencesRepository`: cachedPhotoId per-connection (пропуск повторных загрузок)
  - [x] периодический worker планируется в `DeliriumApplication.onCreate`
- [x] **Phase 5 — Галерея** ✅ DONE
  - [x] `ui/camera/PhotoCompression.kt`: Uri→JPEG + exif (`compressUriToJpeg`)
  - [x] FAB-меню на ленте + PickVisualMedia launcher (`FeedScreen.kt`)
  - [x] gradle: + androidx.exifinterface
- [x] **Phase 6 — Рисование** ✅ DONE
  - [x] `ui/draw/DrawScreen.kt` + `DrawViewModel.kt`
  - [x] экспорт Bitmap→JPEG→upload (`rasterize` + `DrawViewModel.send`)
- [x] **Навигация и строки** ✅ DONE
  - [x] `ui/nav/Routes.kt`, `ui/nav/AppNav.kt`
  - [x] `res/values/strings.xml`
- [x] **Бэкенд (Firebase) — миграция couple → connections** ✅ DONE
  - [x] `firestore/firestore.rules`: правила для `connections`/`photos`/`invites`/`users` (вместо старых `couples`/`coupleId`)
  - [x] `firestore/storage.rules`: `isMember` через `connections`, путь `photos/{connectionId}/...`
  - [x] `firestore/firestore.indexes.json`: composite index `photos(connectionId ASC, createdAt DESC)`
  - [x] `functions/src/index.ts`: читает `connectionId`, шлёт `connectionId` в FCM data-payload
- [ ] **Финал (пользователь)**
  - [ ] Сборка/запуск в Android Studio
  - [ ] Деплой Firebase: `firebase deploy --only firestore:rules,storage,firestore:indexes,functions`
    (минимум для исправления PERMISSION_DENIED: `firebase deploy --only firestore:rules,storage`)

## Заметки по ходу работы
(сюда добавлять решения/нюансы, всплывшие при реализации)
