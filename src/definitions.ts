export interface ShareContentPlugin {
  /**
   * Check whether this platform can present a share sheet.
   * iOS and Android report `true`.
   */
  canShare(): Promise<CanShareResult>;

  /**
   * Present the system share sheet.
   * Pass at least one of `text`, `url`, or `files`.
   * Dismissing the sheet rejects with code `SHARE_CANCELED`.
   * A second call while the sheet is open rejects with `SHARE_IN_PROGRESS`.
   * Invalid input rejects with `INVALID_SHARE`.
   */
  share(options: ShareOptions): Promise<ShareResult>;
}

export interface CanShareResult {
  /** `true` when a share sheet can be presented. */
  value: boolean;
}

export interface ShareResult {
  /**
   * The app that received the share.
   * iOS reports the activity identifier. Android reports the package name
   * when the system provides it. Either platform may return an empty string.
   */
  activityType?: string;
}

export interface ShareOptions {
  /**
   * Title sent as the email subject. Optional, 1–200 characters after trimming.
   * An empty string is ignored.
   */
  title?: string;
  /**
   * Text placed in the message body. Optional, at most 5000 characters
   * after trimming. An empty string is ignored.
   */
  text?: string;
  /**
   * `http`, `https`, or `file://` URL.
   * A `file://` URL is shared as a file. On Android, `text` and an http(s)
   * URL are joined into one message because share targets read a single text field.
   * iOS passes the text and the URL as separate items.
   */
  url?: string;
  /**
   * `file://` URLs to share. At most 10.
   * Each URL must be an absolute local file. `..` path segments are rejected.
   * On Android the file must also sit in a directory listed in the app
   * `file_paths.xml`. Capacitor includes the cache directory by default.
   */
  files?: string[];
  /**
   * Title of the Android chooser. Optional, at most 80 characters after trimming.
   * Defaults to `Share`. Ignored on iOS.
   */
  dialogTitle?: string;
}
