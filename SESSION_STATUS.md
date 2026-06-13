# Delirium — статус апдейта

_Последнее обновление: 2026-06-13. Проект: `delirium-e1a8f`, регион функций `europe-west3`._

Рабочий APK: **`~/Desktop/Delirium.apk`** (debug, ~79 МБ). Ставится поверх предыдущего.

---

## ✅ Сделано и задеплоено

### Багфиксы
- **Вход/регистрация `INTERNAL`** — была не выдана IAM-роль. Выдан
  `roles/iam.serviceAccountTokenCreator` сервис-аккаунту функций. Функции упрочнены:
  токен выпускается ДО записи в Firestore (нет «осиротевших» имён), ошибки понятные.
- **Клиент при сетевых обрывах** — 3 повтора + сообщение «Не удалось связаться с
  сервером…» вместо голого `INTERNAL` (`AuthRepository`).
- **Зеркало фронтальной камеры** убрано (флип по `flipHorizontal`).
- **Флип камеры** работает в камере из переписки.
- **Аплоад из галереи / аватарки «could not open image»** — исправлен баг чтения
  изображения (`compressUriToJpeg` читал поток неправильно; теперь читает байты один раз).

### Фичи
- **Удаление друга** — у обоих; Cloud Function `onConnectionDeleted` стирает фото + Storage.
- **Галерея и рисование на главной** — кнопки у затвора, идут в общий поток превью/получателей.
- **Смена имени** и **загрузка аватарки** (в профиле; аватар виден в профиле/друзьях/чипах).
- **Уведомления о новом фото** (+ запрос разрешения на Android 13+).
- **Ежедневное напоминание** — умное (не дёргает, если уже постил), вкл/выкл + время в Настройках.
- **Огонёк (Snapstreak)** — отдельный счётчик с каждым другом.
- **Реакции на превью** — видны в галерее и ленте друга (денормализация `reactionEmojis`).
- **Кнопка «Подборка» убрана** с экрана истории.
- **Сохранение фото в галерею** — кнопка на экране фото (MediaStore, без разрешений).
- **Реакции long-press / «＋»** — выбор любого эмодзи.
- **Светлая тема** («Дневная») + контраст системных баров по теме.
- **«Просмотрено»** — на экране фото видно, что друг открыл твоё фото.
- **Сбор крашей** — глобальный перехватчик → файл → выгрузка в Firestore `crashReports`.
- **Админ-панель** (`admin/`, localhost): смена паролей, модерация (удаление аккаунта),
  просмотр крашей. Нужен `admin/serviceAccountKey.json` (Console → Service accounts → Generate key),
  затем `cd admin && npm start` → http://127.0.0.1:4000.

### Бэкенд (всё задеплоено)
- **Functions:** `onPhotoCreated` (FCM + streak + senderName), `onConnectionDeleted`,
  `onReactionWritten`, `onViewCreated`, `registerUser`, `loginUser`.
- **Firestore rules** (имена/аватары/удаление связей, `crashReports`, `views`) и **Storage rules** (аватары).
- **Gradle wrapper восстановлен** — сборка из CLI работает.

---

## ✅ Доделано в этой сессии (все 4 фичи из списка)
1. **Оффлайн-очередь отправки фото** — `data/outbox/OutboxRepository` сохраняет байты+метаданные
   на диск, `SendPhotoWorker` (WorkManager, constraint CONNECTED, ретрай с бэкоффом) грузит из
   очереди; камера/превью/рисунок/галерея/видео ставят в очередь. Пересборка очереди при старте
   (`outboxRepository.resyncPending()` в `DeliriumApplication`).
2. **Тап по виджету → лента друга** — виджет кладёт `connectionId` в Intent (`actionParametersOf`),
   `MainActivity` (singleTop, onNewIntent) → `AppNav` ждёт пост-авторизационный экран и открывает
   `FeedRoute`; имя друга резолвится в `FeedViewModel`.
3. **Английская локализация** — все ~110 строк вынесены в `values/strings.xml` (русский дефолт) +
   новый `values-en/strings.xml`. VM/репозитории без Context получили `appContext`; темы и табы —
   через `@StringRes`.
4. **Видео / «бумеранг»** — CameraX `VideoCapture` (зажать затвор в экране камеры друга, до 15 c,
   без звука) + Media3 ExoPlayer (`ui/components/VideoPlayer`). `Photo.mediaType`/`thumbnailUrl`,
   `PhotoRepository.uploadVideo` (mp4 + jpg-превью), видео в очереди, плей-бейдж в ленте/истории,
   проигрывание в детали/подборке, превью для виджета. Бэкенд шлёт превью в FCM для виджета.

> ⚠️ Видео заработает на бэкенде только после **передеплоя Storage rules**
> (`firebase deploy --only storage`) — добавлено разрешение на `video/mp4` (до 30 МБ).
> Сборка APK и `functions` (tsc) проверены — компилируются.

### Перед публичным релизом
- **Release-сборка**: подпись (keystore) + R8/minify + ABI-split → ~20 МБ вместо 79.
- Бэкфилл `reactionEmojis` / `seenBy` для старых фото (по желанию — иначе заполнятся при новой реакции/просмотре).
- Обновить `firebase-functions` SDK (деплой показывает warning об устаревшей версии).

---

## 🔧 Команды
**Сборка APK:**
```sh
cd /Users/vodnixir/programming/apps/delirium
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
**Деплой бэкенда:**
```sh
firebase deploy --only functions,firestore:rules,storage
```
**Админ-панель:**
```sh
cd admin && npm start   # http://127.0.0.1:4000  (нужен serviceAccountKey.json)
```
