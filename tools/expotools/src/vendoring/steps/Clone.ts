import { Step } from './Step';
import fs from 'fs-extra';
import chalk from 'chalk';
import spawnAsync from '@expo/spawn-async';

type CloneSettings =
  | {
      branch: string;
    }
  | {
      tag: string;
    }
  | {
      commit: string;
    }
  | {};

export type CloneRepoSettings = {
  url: string;
  destination: string;
  name?: string;
} & CloneSettings;

export class Clone extends Step {
  private readonly url: string;
  private readonly destination: string;
  private readonly options: CloneSettings;

  constructor({ url, destination, name, ...options }: CloneRepoSettings) {
    super(name || `clone ${url}`);
    this.url = url;
    this.destination = this.toAbsolutePath(destination);
    this.options = options;
  }

  async execute() {
    this.logSubStep(`remove ${this.destination}`);
    await fs.remove(this.destination);

    this.logSubStep(`clone ${chalk.green(this.url)} into ${chalk.magenta(this.destination)}`);

    const cloneArguments = this.cloneArguments();
    this.logDebugInfo(`run git clone ${cloneArguments.join(' ')}`);
    await spawnAsync('git', ['clone', ...cloneArguments, this.url, this.destination]);

    if ('commit' in this.options) {
      this.logDebugInfo(`run git checkout ${this.options.commit}`);
      await spawnAsync('git', ['checkout', this.options.commit], { cwd: this.destination });
    }
  }

  cloneArguments(): string[] {
    const args = ['--depth', '1'];
    if ('branch' in this.options) {
      args.push('--branch', this.options.branch);
    } else if ('tag' in this.options) {
      args.push('--branch', this.options.tag);
    } else if ('commit' in this.options) {
      return [];
    }
    return args;
  }
}
