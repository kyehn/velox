#![no_main]

fuzz_target!(|data: &[u8]| {
    torvox_fuzz::fuzz_osc_parse(data);
});
