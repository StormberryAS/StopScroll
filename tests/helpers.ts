// Shared helpers for the Node tests (TypeScript, run by Node 24 directly).
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = fileURLToPath(new URL('..', import.meta.url));
export const read = (rel: string): string => readFileSync(join(ROOT, rel), 'utf8');

/** Every file under [dir] (relative to the root), skipping build output and node_modules. */
export function walk(dir: string): string[] {
  const out: string[] = [];
  const go = (abs: string): void => {
    for (const name of readdirSync(abs)) {
      if (name === 'node_modules' || name === 'build' || name === '.gradle' || name === '.git' || name === '.kotlin') continue;
      const p = join(abs, name);
      if (statSync(p).isDirectory()) go(p);
      else out.push(relative(ROOT, p));
    }
  };
  go(join(ROOT, dir));
  return out.sort();
}

/** The files git tracks, or every file when the folder is not a git checkout yet. */
export function tracked(): string[] {
  try {
    return execFileSync('git', ['-C', ROOT, 'ls-files', '-z'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).split('\0').filter((f) => f.length > 0);
  } catch {
    return walk('.');
  }
}

/** Kotlin main sources: path and text with comments removed (so a comment naming an API does not count). */
export function kotlinMain(): { path: string; code: string }[] {
  return walk('android/app/src/main/kotlin')
    .filter((p) => p.endsWith('.kt'))
    .map((path) => ({ path, code: stripComments(read(path)) }));
}

export function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:"])\/\/.*$/gm, '$1');
}

export function defined<T>(x: T | undefined | null, what: string): T {
  if (x === undefined || x === null) throw new Error(`${what} is missing`);
  return x;
}
