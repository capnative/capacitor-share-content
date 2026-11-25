import { describe, expect, it } from 'vitest';

import { ShareContent, ShareContentErrorCodes } from '../src/index';
import { prepareShare } from '../src/share';

describe('prepareShare', () => {
  it('keeps text, an https url, and the default dialog title', () => {
    expect(
      prepareShare({
        title: '  Notes  ',
        text: ' Hello ',
        url: 'https://example.com/a',
        dialogTitle: '   ',
      }),
    ).toEqual({
      title: 'Notes',
      text: 'Hello',
      url: 'https://example.com/a',
      dialogTitle: 'Share',
    });
  });

  it('treats a file url as a file and drops duplicates', () => {
    expect(
      prepareShare({
        url: 'file:///tmp/photo.jpg',
        files: ['file:///tmp/photo.jpg', 'file://localhost/tmp/other.png'],
      }),
    ).toEqual({
      files: ['file:///tmp/photo.jpg', 'file://localhost/tmp/other.png'],
      dialogTitle: 'Share',
    });
  });

  it('rejects an empty payload and unsafe urls', () => {
    expect(code({})).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ title: 'Only a subject' })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ text: 1 })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ url: 'javascript:alert(1)' })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ url: 'data:text/plain,hello' })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ url: 'file:///tmp/../etc/passwd' })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ files: ['https://example.com/a'] })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ files: ['file://files.example/tmp/a.jpg'] })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ text: 'x'.repeat(5001) })).toBe(ShareContentErrorCodes.INVALID_SHARE);
    expect(code({ files: Array.from({ length: 11 }, (_, index) => `file:///tmp/${index}.txt`) })).toBe(
      ShareContentErrorCodes.INVALID_SHARE,
    );
  });
});

describe('ShareContent', () => {
  it('rejects an empty share before calling the platform', async () => {
    await expect(ShareContent.share({})).rejects.toMatchObject({
      code: ShareContentErrorCodes.INVALID_SHARE,
      message: 'Share at least one of text, url, or files.',
    });
  });
});

function code(options: object): string {
  try {
    prepareShare(options as Parameters<typeof prepareShare>[0]);
    return 'none';
  } catch (error) {
    return typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : 'throw';
  }
}
