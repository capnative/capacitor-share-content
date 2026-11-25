import { registerPlugin } from '@capacitor/core';

import type { ShareContentPlugin } from './definitions';
import { prepareShare } from './share';

const implementation = registerPlugin<ShareContentPlugin>('ShareContent');

export const ShareContent: ShareContentPlugin = {
  canShare: () => implementation.canShare(),
  async share(options) {
    return implementation.share(prepareShare(options));
  },
};

export * from './definitions';
export { ShareContentError, ShareContentErrorCodes, isShareContentError } from './errors';
