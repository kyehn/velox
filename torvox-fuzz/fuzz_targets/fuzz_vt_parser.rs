#![no_main]

fuzz_target!(|data: &[u8]| {
    torvox_fuzz::fuzz_vt_parser(data);
});
