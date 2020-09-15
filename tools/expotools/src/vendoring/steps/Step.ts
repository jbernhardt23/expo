import path from 'path';
import chalk from 'chalk';
import * as Directories from '../../Directories';
import logger from '../../Logger';

export abstract class Step {
  constructor(protected stepName: string) {}

  protected abstract async execute();

  protected toAbsolutePath(pathToConvert: string): string {
    if (path.isAbsolute(pathToConvert)) {
      return pathToConvert;
    }
    return path.join(Directories.getExpoRepositoryRootDir(), pathToConvert);
  }

  protected logSubStep(message: string) {
    logger.info(`  > ${message}`);
  }

  protected logDebugInfo(message: string | string[]) {
    if (typeof message === 'string') {
      logger.debug(`    ${message}`);
    } else {
      logger.debug(`    ${message.join('\n    ')}`);
    }
  }

  public getName() {
    return this.stepName;
  }

  public async start() {
    logger.info(`🚀 Staring ${chalk.green(this.stepName)}...`);
    try {
      await this.execute();
    } catch (e) {
      logger.error(e);
      logger.error(chalk.red(`❌ ${this.stepName} failed.`));
      return;
    }
    logger.info(`✅ ${chalk.green(this.stepName)} finished.\n`);
  }
}
