import { Step } from './Step';
import chalk from 'chalk';
import fs from 'fs-extra';

export type RemoveDirectorySettings = {
  target: string;
  name?: string;
};

export class RemoveDirectory extends Step {
  private readonly target: string;

  constructor({ target, name }: RemoveDirectorySettings) {
    super(name || 'remove');
    this.target = this.toAbsolutePath(target);
  }

  async execute() {
    this.logSubStep(`remove ${chalk.green(this.target)}`);
    return await fs.remove(this.target);
  }
}
