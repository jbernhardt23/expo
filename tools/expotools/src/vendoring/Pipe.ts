import { Step } from './steps/Step';
import chalk from 'chalk';

import logger from '../Logger';

export type PlatformSpecificStep = {
  step: Step;
  platform: 'ios' | 'android' | 'all';
};

export class Pipe {
  private readonly platformSpecificSteps: PlatformSpecificStep[];

  constructor(...steps: (PlatformSpecificStep | Step)[]) {
    this.platformSpecificSteps = steps.map((step) => {
      if (!('platform' in step)) {
        return { platform: 'all', step };
      }

      return step;
    });
  }

  public addSteps(platform: 'ios' | 'android' | 'all', ...steps: Step[]) {
    steps.forEach((step) => {
      this.platformSpecificSteps.push({ platform, step });
    });
  }

  public async start(platform: 'all' | 'ios' | 'android') {
    logger.debug(`Staring pipe for platform = ${chalk.green(platform)}`);
    logger.debug(
      this.platformSpecificSteps
        .map(
          ({ platform, step }) =>
            `  - [${chalk.blueBright(platform)}] ${chalk.yellow(step.getName())}`
        )
        .join('\n')
    );
    logger.debug();

    const steps = this.platformSpecificSteps
      .filter((platformSpecificStep) => {
        const { platform: stepPlatform } = platformSpecificStep;
        if (platform === 'all' || stepPlatform === 'all') {
          return true;
        }

        if (platform === stepPlatform) {
          return true;
        }

        return false;
      })
      .map(({ step }) => step);

    for (const step of steps) {
      await step.start();
    }
  }
}
