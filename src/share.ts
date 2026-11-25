import { ShareContentError, ShareContentErrorCodes } from './errors';
import type { ShareOptions } from './definitions';

export const SHARE_LIMITS = {
  title: 200,
  text: 5000,
  url: 2048,
  fileUrl: 4096,
  files: 10,
  dialogTitle: 80,
} as const;

export interface PreparedShare {
  title?: string;
  text?: string;
  url?: string;
  files?: string[];
  dialogTitle: string;
}

const UNSAFE = /[\u0000-\u001F\u007F\s]/;

export function prepareShare(options: ShareOptions): PreparedShare {
  if (options === null || typeof options !== 'object' || Array.isArray(options)) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'share() expects an options object.');
  }

  const title = optionalText(options.title, 'title', SHARE_LIMITS.title);
  const text = optionalText(options.text, 'text', SHARE_LIMITS.text);
  const dialogTitle = optionalText(options.dialogTitle, 'dialogTitle', SHARE_LIMITS.dialogTitle) ?? 'Share';
  const files = fileList(options.files);
  const link = options.url === undefined || options.url === null ? undefined : httpOrFile(options.url);

  if (link?.kind === 'file') {
    files.unshift(link.value);
  }

  const uniqueFiles = dedupe(files);
  if (uniqueFiles.length > SHARE_LIMITS.files) {
    throw new ShareContentError(
      ShareContentErrorCodes.INVALID_SHARE,
      `At most ${SHARE_LIMITS.files} files can be shared.`,
    );
  }

  const url = link?.kind === 'http' ? link.value : undefined;
  if (text === undefined && url === undefined && uniqueFiles.length === 0) {
    throw new ShareContentError(
      ShareContentErrorCodes.INVALID_SHARE,
      'Share at least one of text, url, or files.',
    );
  }

  return {
    ...(title === undefined ? {} : { title }),
    ...(text === undefined ? {} : { text }),
    ...(url === undefined ? {} : { url }),
    ...(uniqueFiles.length === 0 ? {} : { files: uniqueFiles }),
    dialogTitle,
  };
}

function optionalText(value: unknown, name: string, max: number): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'string') {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, `${name} must be a string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length === 0) return undefined;
  if (trimmed.length > max) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, `${name} must be ${max} characters or fewer.`);
  }
  return trimmed;
}

function fileList(value: unknown): string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'files must be an array of file:// URLs.');
  }
  return value.map((item) => {
    if (typeof item !== 'string') {
      throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'Each file must be a file:// URL.');
    }
    return fileUrl(item);
  });
}

function httpOrFile(value: unknown): { kind: 'http' | 'file'; value: string } {
  if (typeof value !== 'string') {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'url must be a string.');
  }
  const trimmed = value.trim();
  if (UNSAFE.test(trimmed)) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'url must be an http, https, or file URL.');
  }
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'url must be an http, https, or file URL.');
  }
  if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
    if (trimmed.length > SHARE_LIMITS.url) {
      throw new ShareContentError(
        ShareContentErrorCodes.INVALID_SHARE,
        `url must be ${SHARE_LIMITS.url} characters or fewer.`,
      );
    }
    if (!parsed.hostname) {
      throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'url must include a host.');
    }
    return { kind: 'http', value: trimmed };
  }
  if (parsed.protocol === 'file:') {
    return { kind: 'file', value: fileUrl(trimmed) };
  }
  throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'url must be an http, https, or file URL.');
}

function fileUrl(value: string): string {
  const trimmed = value.trim();
  if (trimmed.length > SHARE_LIMITS.fileUrl) {
    throw new ShareContentError(
      ShareContentErrorCodes.INVALID_SHARE,
      `A file URL must be ${SHARE_LIMITS.fileUrl} characters or fewer.`,
    );
  }
  if (UNSAFE.test(trimmed)) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'Each file must be a file:// URL.');
  }
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'Each file must be a file:// URL.');
  }
  if (parsed.protocol !== 'file:') {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'Each file must be a file:// URL.');
  }
  if (hasDotSegment(trimmed)) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must be an absolute local path.');
  }
  if (parsed.hostname !== '' && parsed.hostname !== 'localhost') {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must point at a local file.');
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must be an absolute local path.');
  }
  let path: string;
  try {
    path = decodeURIComponent(parsed.pathname);
  } catch {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must be an absolute local path.');
  }
  if (!path.startsWith('/') || path === '/' || path.endsWith('/') || path.includes('\\') || path.includes('\0')) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must be an absolute local path.');
  }
  if (path.split('/').includes('..')) {
    throw new ShareContentError(ShareContentErrorCodes.INVALID_SHARE, 'File URLs must be an absolute local path.');
  }
  return trimmed;
}

function hasDotSegment(value: string): boolean {
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    return true;
  }
  return decoded.split(/[/\\]/).includes('..');
}

function dedupe(values: string[]): string[] {
  const seen = new Set<string>();
  const unique: string[] = [];
  for (const value of values) {
    if (seen.has(value)) continue;
    seen.add(value);
    unique.push(value);
  }
  return unique;
}
