import {build} from 'esbuild';
await build({entryPoints:['nh-starter/worker.ts'],bundle:true,platform:'node',format:'esm',target:'node22',outfile:'nh-starter/worker.mjs',logLevel:'warning'});
console.log('NH starter combat bridge built.');
