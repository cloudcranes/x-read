from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings


def test_explicit_anonymous_lan(tmp_path):
    config = Settings(host='192.168.1.170', allow_anonymous=True, db_path=str(tmp_path/'state.db'))
    with TestClient(create_app(config)) as client:
        assert client.get('/api/v1/bookshelf').status_code == 200
        assert client.get('/api/v1/history').status_code == 200
        assert client.get('/api/v1/cache').status_code == 200
        assert client.get('/docs').status_code == 200


def test_token_still_enforced_if_configured(tmp_path):
    config = Settings(host='192.168.1.170', allow_anonymous=True, api_token='configured', db_path=str(tmp_path/'state.db'))
    with TestClient(create_app(config)) as client:
        assert client.get('/api/v1/bookshelf').status_code == 401
