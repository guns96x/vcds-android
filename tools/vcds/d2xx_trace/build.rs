use std::env;
use std::path::PathBuf;

fn main() {
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let def_path = manifest_dir.join("RTUS64.def");
    println!("cargo:rerun-if-changed={}", def_path.display());
    println!("cargo:rustc-cdylib-link-arg=/DEF:{}", def_path.display());
}
