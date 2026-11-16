import {build} from 'esbuild';
await build({entryPoints:['nh-starter/worker-stage3.ts'],bundle:true,platform:'node',format:'esm',target:'node22',outfile:'nh-starter/worker-stage3.mjs',logLevel:'warning'});
