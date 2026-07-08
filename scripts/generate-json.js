#!/usr/bin/env node
const fs=require('fs');const path=require('path');
const root=process.cwd();const gamesDir=path.join(root,'games');const out=path.join(root,'games_catalog.json');
const baseUrl=process.env.GAMES_BASE_URL||'./games/';
const logoNames=['logo.png','logo.jpg','logo.jpeg','logo.svg','icon.png','icon.svg'];
function walk(dir,prefix=''){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(d=>{const rel=path.posix.join(prefix,d.name);const abs=path.join(dir,d.name);return d.isDirectory()?walk(abs,rel):rel})}
function readJson(file){return fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):{}}
if(!fs.existsSync(gamesDir)){fs.mkdirSync(gamesDir,{recursive:true})}
const catalog=fs.readdirSync(gamesDir,{withFileTypes:true}).filter(d=>d.isDirectory()).map(d=>{const dir=path.join(gamesDir,d.name);const manifest=readJson(path.join(dir,'game.json'));const files=walk(dir).filter(f=>!f.startsWith('.'));const logo=manifest.logo||files.find(f=>logoNames.includes(path.basename(f).toLowerCase()))||'';const stats=files.map(f=>fs.statSync(path.join(dir,f)));const statSize=stats.reduce((sum,st)=>sum+st.size,0);const updatedAt=new Date(Math.max(...stats.map(st=>st.mtimeMs))).toISOString();return {id:manifest.id||d.name,name:manifest.name||d.name.replace(/[-_]/g,' ').replace(/\b\w/g,c=>c.toUpperCase()),description:manifest.description||'A fun offline web game.',version:manifest.version||'1.0.0',categories:manifest.categories||['Arcade'],entry:manifest.entry||'index.html',logo:logo?`${baseUrl}${d.name}/${logo}`:'assets/icon.svg',baseUrl:`${baseUrl}${d.name}/`,files,size:statSize,updatedAt}}).sort((a,b)=>a.name.localeCompare(b.name));
fs.writeFileSync(out,JSON.stringify(catalog,null,2)+'\n');
console.log(`Wrote ${catalog.length} games to ${path.relative(root,out)}`);
