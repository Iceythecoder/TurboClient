import {build} from 'esbuild';
await build({entryPoints:['nh-starter/worker-v1.ts'],bundle:true,platform:'node',format:'esm',target:'node22',outfile:'nh-starter/worker-v1.mjs',logLevel:'warning'});console.log('Archived NH v1 comparison adapter built.');
