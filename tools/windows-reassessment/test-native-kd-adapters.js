// Offline test of generated debugger JS against the exact mapped kernel image.
// No debugger, VM, socket or device writes. Tests both PASS and fail-closed guard.
"use strict";
const fs=require("fs"),vm=require("vm"),assert=require("assert");
const [kernelPath,scriptPath]=process.argv.slice(2),raw=fs.readFileSync(kernelPath);
const pe=raw.readUInt32LE(60),count=raw.readUInt16LE(pe+6),opt=raw.readUInt16LE(pe+20);
const original=Buffer.alloc(raw.readUInt32LE(pe+80));raw.copy(original,0,0,0x1000);
for(let i=0;i<count;i++){
    const at=pe+24+opt+40*i,start=raw.readUInt32LE(at+12),size=raw.readUInt32LE(at+16),offset=raw.readUInt32LE(at+20);
    raw.copy(original,start,offset,offset+size);
}
const base=0xfffff80000000000n;
class I64{
    constructor(value){this.value=BigInt(value);}
    add(value){return new I64(this.value+BigInt(value));}
    compareTo(other){return this.value<other.value?-1:this.value>other.value?1:0;}
    toString(radix){return this.value.toString(radix);}
}
function run(corrupt){
    const memory=Buffer.from(original),writes=[],logs=[];let resume=0;
    if(corrupt)memory[0x49bc4c]^=1;
    const host={
        evaluateExpression:e=>e==="nt"?new I64(base):new I64(base+0x2013c8n),
        parseInt64:(v,radix)=>new I64(BigInt("0x"+v)),
        memory:{readMemoryValues(at,n,size){
            const start=Number(at.value-base);assert(start>=0&&start+n*size<=memory.length);
            return Array.from({length:n},(_,i)=>size===1?memory[start+i]:memory.readUInt32LE(start+i*size));
        }},
        diagnostics:{debugLog:(...values)=>logs.push(values.join(""))},
        namespace:{Debugger:{Utility:{Control:{ExecuteCommand(command){
            if(command==="lm m nt")return ["start             end                 module name", "fffff800`00000000 fffff800`02000000   nt         (deferred)"];
            if(command.startsWith("r @$t19=")){resume=Number(command.split("=")[1]);return [];}
            const match=/^eb ([0-9a-f]+) (.*)$/.exec(command);assert(match,"Unexpected command "+command);
            const offset=Number(BigInt("0x"+match[1])-base),data=Buffer.from(match[2].split(" ").map(b=>parseInt(b,16)));
            data.copy(memory,offset);writes.push({offset,data});return [];
        }}}}}
    };
    const context=vm.createContext({host});vm.runInContext(fs.readFileSync(scriptPath,"utf8"),context);
    if(corrupt){assert.throws(()=>context.onKernel(),/NATIVE_GUARD_FAILED/);assert.strictEqual(writes.length,0);assert.strictEqual(resume,0);}
    else{assert.strictEqual(context.onKernel(),true);assert.strictEqual(resume,1);assert(logs.some(s=>s.includes("NATIVE_KERNEL_PATCH_PASS")));assert(writes.length>0);assert.strictEqual(context.onKernel(),true);}
}
run(false);run(true);
console.log("NATIVE_ADAPTER_EXACT_MAPPED_IMAGE_AND_FAIL_CLOSED_GUARD=PASS");
