import { Step } from './Step';
import path from 'path';
import chalk from 'chalk';
import glob from 'glob-promise';
import fs from 'fs-extra';

export type FileContentTransformStepSettings = {
  source: string;
  filePattern: string;
  find: string;
  replace: string;
  name?: string;
};

export class TransformFilesContent extends Step {
  protected readonly source: string;
  protected readonly filePattern: string;
  protected readonly find: RegExp;
  protected readonly replace: string;

  constructor({ source, filePattern, find, replace, name }: FileContentTransformStepSettings) {
    super(name || `transform files content`);
    this.source = this.toAbsolutePath(source);
    this.filePattern = filePattern;
    this.find = new RegExp(find, 'gm');
    this.replace = replace;
  }

  async execute() {
    this.logSubStep(
      `find ${chalk.yellow(this.find.toString())} in ${chalk.green(this.source)}/${chalk.yellow(
        this.filePattern
      )} and replace with ${chalk.magenta(this.replace)}`
    );

    const files = await this.findFiles();
    this.logDebugInfo('file affected: ');
    this.logDebugInfo(files.map((file) => `- ${file}`));

    await Promise.all(
      files.map(async (file) => {
        const content = await fs.readFile(file, 'utf8');
        const transformedContent = content.replace(this.find, this.replace);
        return await fs.writeFile(file, transformedContent, 'utf8');
      })
    );
  }

  protected async findFiles(): Promise<string[]> {
    return await glob(path.join(this.source, this.filePattern));
  }
}
