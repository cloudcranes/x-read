"""Environment-only configuration; credentials never appear in API responses."""
import ipaddress
import os
from dataclasses import dataclass
from pathlib import Path


def _integer(name: str, default: int, minimum: int = 1) -> int:
    value = int(os.environ.get(name, str(default)))
    if value < minimum:
        raise ValueError(f"{name} must be >= {minimum}")
    return value


def is_loopback(host: str) -> bool:
    if host.lower() == "localhost":
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


@dataclass(frozen=True)
class Settings:
    db_path: str = "data/x-read.sqlite3"
    host: str = "127.0.0.1"
    port: int = 8080
    api_token: str = ""
    allow_anonymous: bool = False
    metadata_ttl: int = 600
    chapter_ttl: int = 604800
    cache_budget_bytes: int = 268435456
    cors_origins: tuple[str, ...] = ()
    # 安卓安装包不随 Python 包分发：内容核心与设备密钥全在手机上，
    # 分发包里的旧 APK 会长期腐坏（曾内置一个 16MB 的服务端依赖版）。
    apk_path: str = ""

    def __post_init__(self):
        if not is_loopback(self.host) and not self.api_token and not self.allow_anonymous:
            raise ValueError("X_READ_API_TOKEN is required for non-loopback binding")
        if not 1 <= self.port <= 65535:
            raise ValueError("X_READ_PORT must be in 1..65535")
        if min(self.metadata_ttl, self.chapter_ttl, self.cache_budget_bytes) <= 0:
            raise ValueError("Cache TTL and budget must be positive")
        if "*" in self.cors_origins:
            raise ValueError("Configure explicit CORS origins, not '*'")

    @classmethod
    def from_env(cls):
        return cls(
            db_path=os.environ.get("X_READ_DB", "data/x-read.sqlite3"),
            host=os.environ.get("X_READ_HOST") or os.environ.get("X_READ_BIND_ADDRESS") or "127.0.0.1",
            port=_integer("X_READ_PORT", 8080),
            api_token=os.environ.get("X_READ_API_TOKEN", ""),
            allow_anonymous=os.environ.get("X_READ_ALLOW_ANONYMOUS", "false").lower() in {"1", "true", "yes"},
            metadata_ttl=_integer("X_READ_METADATA_TTL", 600),
            chapter_ttl=_integer("X_READ_CHAPTER_TTL", 604800),
            cache_budget_bytes=_integer("X_READ_CACHE_BUDGET_BYTES", 268435456),
            cors_origins=tuple(x.strip() for x in os.environ.get("X_READ_CORS_ORIGINS", "").split(",") if x.strip()),
            apk_path=os.environ.get("X_READ_APK_PATH", ""),
        )

    def ensure_directory(self):
        if self.db_path != ":memory:":
            Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
