'use strict';
const activationPrefix = '010400040004';
const activationSuffix = '00000000000028';
let matches = 0;
function log(s) { console.log('[mifare-compat] ' + s); }
try {
    const library = Process.getModuleByName('libstnfc_nci_jni.so');
    log('module base=' + library.base);
    const exported = library.enumerateExports();
    function symbol(part) {
        const hits = exported.filter(e => e.name.includes(part));
        if (hits.length !== 1) throw new Error('ambiguous symbol: ' + part + ' hits=' + hits.length);
        return hits[0].address;
    }
    function bytesToHex(address, length) {
        return Array.from(new Uint8Array(address.readByteArray(length)), v => v.toString(16).padStart(2, '0')).join('').toUpperCase();
    }
    const readPending = new NativeFunction(symbol('getNumDiscNtfEv'), 'int', ['pointer']);
    const writePending = new NativeFunction(symbol('setNumDiscNtfEi'), 'void', ['pointer', 'int']);
    Interceptor.attach(symbol('createNativeNfcTag'), {
        onEnter(args) {
            const activation = bytesToHex(args[1], 17);
            const before = readPending(args[0]);
            if (!activation.startsWith(activationPrefix) || !activation.endsWith(activationSuffix) || before !== 1) {
                log('skip ' + activation + ' pending=' + before);
                return;
            }
            writePending(args[0], 0);
            matches++;
            log('APPLIED uid=' + activation.substring(12, 20) + ' matches=' + matches);
        }
    });
    log('hook installed');
} catch (e) {
    log('ERROR ' + e);
}
