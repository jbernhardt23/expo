import { TransformFilesContent, FileContentTransformStepSettings } from './TransformFilesContent';
import chalk from 'chalk';
import path from 'path';

import fs from 'fs-extra';

export class TransformFilesName extends TransformFilesContent {
  constructor({ name, ...settings }: FileContentTransformStepSettings) {
    super({ ...settings, name: name || 'rename files' });
  }

  async execute() {
    this.logSubStep(
      `find ${chalk.yellow(this.find.toString())} in files names in path ${chalk.green(
        this.source
      )}/${chalk.yellow(this.filePattern)} and replace with ${chalk.magenta(this.replace)}`
    );

    const files = await this.findFiles();
    this.logDebugInfo('file affected: ');
    this.logDebugInfo(files.map((file) => `- ${file}`));

    await Promise.all(
      files.map((file) => {
        const fileName = path.basename(file).replace(this.find, this.replace);
        const parent = path.dirname(file);

        return fs.rename(file, path.join(parent, fileName));
      })
    );
  }
}
