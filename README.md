# @capnative/capacitor-share-content

Share text, links, and files with other apps for Capacitor 8.

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅         |

## Supported platforms

| Platform | Support | Minimum version            |
| -------- | ------- | -------------------------- |
| iOS      | ✅      | iOS 15                     |
| Android  | ✅      | API level 24 (Android 7.0) |

## Install

```bash
npm install @capnative/capacitor-share-content
npx cap sync
```

## Basic usage

```typescript
import { ShareContent } from '@capnative/capacitor-share-content';

const available = await ShareContent.canShare();
if (!available.value) {
  throw new Error('Sharing is not available.');
}

await ShareContent.share({
  title: 'Notes',
  text: 'A paragraph worth sending on',
  url: 'https://example.com/notes/1',
  dialogTitle: 'Share with',
});
```

Dismissing the sheet rejects with code `SHARE_CANCELED`. Calling `share` again while a sheet is open rejects with `SHARE_IN_PROGRESS`.

## Sharing files

`files` and a `file://` `url` are local files. iOS shares any `file://` URL the app can read. Android shares through the app `FileProvider` (`${applicationId}.fileprovider`). Capacitor already lists the cache directory in `android/app/src/main/res/xml/file_paths.xml`. A file outside those paths is rejected. Add a `<paths>` entry before sharing another directory.

## API

<docgen-index>

* [`canShare()`](#canshare)
* [`share(...)`](#share)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### canShare()

```typescript
canShare() => Promise<CanShareResult>
```

Check whether this platform can present a share sheet.
iOS and Android report `true`.

**Returns:** <code>Promise&lt;<a href="#canshareresult">CanShareResult</a>&gt;</code>

--------------------


### share(...)

```typescript
share(options: ShareOptions) => Promise<ShareResult>
```

Present the system share sheet.
Pass at least one of `text`, `url`, or `files`.
Dismissing the sheet rejects with code `SHARE_CANCELED`.
A second call while the sheet is open rejects with `SHARE_IN_PROGRESS`.
Invalid input rejects with `INVALID_SHARE`.

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#shareoptions">ShareOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#shareresult">ShareResult</a>&gt;</code>

--------------------


### Interfaces


#### CanShareResult

| Prop        | Type                     | Description                                   |
| ----------- | ------------------------ | --------------------------------------------- |
| **`value`** | <code>boolean</code>     | `true` when a share sheet can be presented.   |


#### ShareResult

| Prop               | Type                   | Description                                                                                                                                                          |
| ------------------ | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`activityType`** | <code>string</code>    | The app that received the share. iOS reports the activity identifier. Android reports the package name when the system provides it. Either platform may return an empty string. |


#### ShareOptions

| Prop              | Type                   | Description                                                                                                                                                                                                                                                       |
| ----------------- | ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`title`**       | <code>string</code>    | Title sent as the email subject. Optional, 1–200 characters after trimming. An empty string is ignored.                                                                                                                                                         |
| **`text`**        | <code>string</code>    | Text placed in the message body. Optional, at most 5000 characters after trimming. An empty string is ignored.                                                                                                                                                   |
| **`url`**         | <code>string</code>    | `http`, `https`, or `file://` URL. A `file://` URL is shared as a file. On Android, `text` and an http(s) URL are joined into one message because share targets read a single text field. iOS passes the text and the URL as separate items. |
| **`files`**       | <code>string[]</code>  | `file://` URLs to share. At most 10. Each URL must be an absolute local file. `..` path segments are rejected. On Android the file must also sit in a directory listed in the app `file_paths.xml`. Capacitor includes the cache directory by default. |
| **`dialogTitle`** | <code>string</code>    | Title of the Android chooser. Optional, at most 80 characters after trimming. Defaults to `Share`. Ignored on iOS. |
