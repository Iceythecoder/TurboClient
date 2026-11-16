// Use the installed TypeScript printer; no formatter download is required.
const fs = require('fs');
const ts = require('typescript');
const path = process.argv[2] || 'spectator/main.ts';
if (!['spectator/main.ts', 'nh-starter/worker.ts'].includes(path)) throw new Error('Unsupported source file');
const source = ts.createSourceFile(path, fs.readFileSync(path, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
fs.writeFileSync(path, ts.createPrinter({newLine: ts.NewLineKind.LineFeed}).printFile(source));
