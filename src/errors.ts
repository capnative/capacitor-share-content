export const ShareContentErrorCodes = {
  INVALID_SHARE: 'INVALID_SHARE',
  SHARE_CANCELED: 'SHARE_CANCELED',
  SHARE_IN_PROGRESS: 'SHARE_IN_PROGRESS',
  UNAVAILABLE: 'UNAVAILABLE',
} as const;

export type ShareContentErrorCode = (typeof ShareContentErrorCodes)[keyof typeof ShareContentErrorCodes];

export class ShareContentError extends Error {
  readonly code: ShareContentErrorCode;

  constructor(code: ShareContentErrorCode, message: string) {
    super(message);
    this.name = 'ShareContentError';
    this.code = code;
  }
}

export function isShareContentError(error: unknown): error is ShareContentError {
  return error instanceof ShareContentError;
}
