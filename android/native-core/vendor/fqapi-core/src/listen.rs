//! Listener mode shared by every entry point.
//!
//! Kept outside [`crate::server`] so a host that does not link the loopback
//! HTTP adapter (the Android JNI build) can still describe "no listener".

/// Which address family the listener accepts connections on.
///
/// `Loopback` is the default and keeps the API private to this device.
/// `Lan` binds every interface so another machine on the same network can
/// reach the read routes; the Host/Origin guard widens with it.
///
/// `Disabled` exists for hosts that publish the dispatcher over their own
/// listener. The loopback adapter is not merely redundant there: its read
/// routes answer any local client without a capability, so on a shared host it
/// would be an unauthenticated side door next to the public, authenticated one.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ListenMode {
    #[default]
    Loopback,
    Lan,
    Disabled,
}
