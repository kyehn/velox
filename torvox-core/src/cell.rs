use serde::{Deserialize, Serialize};
#[cfg(feature = "std")]
use thiserror::Error;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Cell {
    pub char: char,
    pub fg: Color,
    pub bg: Color,
    pub attrs: Attrs,
}

impl Default for Cell {
    fn default() -> Self {
        Self {
            char: ' ',
            fg: Color::default(),
            bg: Color::default(),
            attrs: Attrs::default(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Color {
    pub r: u8,
    pub g: u8,
    pub b: u8,
    pub a: u8,
}

impl Default for Color {
    fn default() -> Self {
        Self {
            r: 255,
            g: 255,
            b: 255,
            a: 255,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct Attrs {
    pub bold: bool,
    pub dim: bool,
    pub italic: bool,
    pub underline: bool,
    pub double_underline: bool,
    pub reverse: bool,
    pub strikethrough: bool,
    pub blink: bool,
    pub hidden: bool,
    pub overline: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct DirtyMask(pub u64);

impl DirtyMask {
    pub const CLEAN: Self = DirtyMask(0);

    pub fn is_dirty(&self, row: u32) -> bool {
        self.0 & (1 << row) != 0
    }

    pub fn mark(&mut self, row: u32) {
        self.0 |= 1 << row;
    }

    pub fn mark_all(&mut self, rows: u32) {
        if rows >= 64 {
            self.0 = !0;
        } else {
            self.0 = (1 << rows) - 1;
        }
    }

    pub fn clear(&mut self) {
        self.0 = 0;
    }

    pub fn any_dirty(&self) -> bool {
        self.0 != 0
    }
}

#[derive(Debug, Clone)]
#[cfg_attr(feature = "std", derive(Error))]
pub enum CoreError {
    #[cfg_attr(feature = "std", error("row index out of bounds: {index} >= {max}"))]
    RowOutOfBounds { index: u32, max: u32 },
    #[cfg_attr(feature = "std", error("column index out of bounds: {index} >= {max}"))]
    ColOutOfBounds { index: u32, max: u32 },
}

impl Color {
    pub fn new(r: u8, g: u8, b: u8) -> Self {
        Self { r, g, b, a: 255 }
    }

    pub fn from_ansi(index: u8) -> Self {
        let [r, g, b] = crate::ansi::ansi_to_rgb(index);
        Self { r, g, b, a: 255 }
    }
}

impl Cell {
    pub fn with_char(c: char) -> Self {
        Self {
            char: c,
            ..Default::default()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cell_default_is_space() {
        let c = Cell::default();
        assert_eq!(c.char, ' ');
        assert_eq!(c.fg, Color::default());
        assert_eq!(c.bg, Color::default());
    }

    #[test]
    fn cell_with_char() {
        let c = Cell::with_char('X');
        assert_eq!(c.char, 'X');
        assert_eq!(c.fg, Color::default());
    }

    #[test]
    fn color_new() {
        let c = Color::new(255, 128, 0);
        assert_eq!(c.r, 255);
        assert_eq!(c.g, 128);
        assert_eq!(c.b, 0);
        assert_eq!(c.a, 255);
    }

    #[test]
    fn color_from_ansi() {
        let c = Color::from_ansi(1);
        assert_eq!(c.r, 128);
        assert_eq!(c.g, 0);
        assert_eq!(c.b, 0);
    }

    #[test]
    fn color_serde_roundtrip() {
        let c = Color::new(10, 20, 30);
        let bytes = postcard::to_allocvec(&c).unwrap();
        let decoded: Color = postcard::from_bytes(&bytes).unwrap();
        assert_eq!(c, decoded);
    }

    #[test]
    fn cell_serde_roundtrip() {
        let c = Cell {
            char: 'A',
            fg: Color::new(255, 0, 0),
            bg: Color::new(0, 0, 255),
            attrs: Attrs {
                bold: true,
                italic: false,
                underline: true,
                reverse: false,
            },
        };
        let bytes = postcard::to_allocvec(&c).unwrap();
        let decoded: Cell = postcard::from_bytes(&bytes).unwrap();
        assert_eq!(c, decoded);
    }

    #[test]
    fn attrs_default_all_false() {
        let a = Attrs::default();
        assert!(!a.bold);
        assert!(!a.dim);
        assert!(!a.italic);
        assert!(!a.underline);
        assert!(!a.double_underline);
        assert!(!a.reverse);
        assert!(!a.strikethrough);
        assert!(!a.blink);
        assert!(!a.hidden);
        assert!(!a.overline);
    }

    #[test]
    fn dirty_mask_ops() {
        let mut m = DirtyMask::CLEAN;
        assert!(!m.any_dirty());
        m.mark(5);
        assert!(m.is_dirty(5));
        assert!(!m.is_dirty(0));
        m.clear();
        assert!(!m.any_dirty());
    }
}
