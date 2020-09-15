import { Step } from './Step';
import path from 'path';
import chalk from 'chalk';
import glob from 'glob-promise';
import fs from 'fs-extra';

export type CopyFilesSettings = {
  from: string;
  filePattern: string[];
  to: string;
  name?: string;
};

export class CopyFiles extends Step {
  private readonly from: string;
  private readonly filePatterns: string[];
  private readonly to: string;

  constructor({ from, filePattern, to, name }: CopyFilesSettings) {
    super(name || 'copy files');
    this.from = this.toAbsolutePath(from);
    this.to = this.toAbsolutePath(to);
    this.filePatterns = filePattern;
  }

  async execute() {
    for (const pattern of this.filePatterns) {
      this.logSubStep(
        `copy ${chalk.green(this.from)}/${chalk.yellow(pattern)} into ${chalk.magenta(this.to)}`
      );

      const files = await this.findFiles(pattern);
      this.logDebugInfo('file affected: ');
      this.logDebugInfo(files.map((file) => `- ${file}`));

      await Promise.all(
        files.map(async (file) => {
          const relativeFilePath = path.relative(this.from, file);
          const destinationFullPath = path.join(this.to, relativeFilePath);

          await fs.mkdirs(path.dirname(destinationFullPath));
          return await fs.copy(file, destinationFullPath);
        })
      );
    }
  }

  async findFiles(pattern: string): Promise<string[]> {
    return await glob(path.join(this.from, pattern));
  }
}
