"""Load harness metric and isolation checks; no backend, traffic or credentials."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import load_test


class Harness(unittest.TestCase):
    def test_percentiles_use_nearest_rank_and_empty_samples_stay_unknown(self):
        self.assertIsNone(load_test.percentile([], .95))
        self.assertEqual(load_test.percentile(list(range(1, 101)), .95), 95)
        self.assertEqual(load_test.percentile([20, 10], .99), 20)

    def test_metrics_distinguish_throttle_from_errors_and_success_latency(self):
        metrics = load_test.Metrics()
        metrics.rows = [('GET', 200, 20), ('GET', 429, 1), ('GET', 0, 5000), ('POST', 503, 300)]
        result = metrics.summary(2)
        self.assertEqual(result['unexpected_errors'], 2)
        self.assertEqual(result['requests_per_second'], 2)
        self.assertEqual(result['methods']['GET']['successful_p95_ms'], 20)
        self.assertEqual(result['methods']['GET']['statuses']['429'], 1)

    def test_incomplete_runs_cannot_be_marked_successful(self):
        self.assertEqual(load_test.checks({}), {'complete': False})
        self.assertEqual(load_test.checks({'failure': {'type': 'RuntimeError'}}), {'complete': False})

    def test_http_load_refuses_public_targets_before_opening_a_socket(self):
        with patch.object(load_test.http.client, 'HTTPConnection') as connection:
            for base in ('https://irc.kokodoko.cloud/community', 'http://72.61.124.153:8095', 'http://localhost:8095'):
                with self.assertRaises(ValueError):
                    load_test.request(base, 'GET', '/api/health')
            connection.assert_not_called()

    def test_transport_failures_are_measured_not_assumed_to_be_timeouts(self):
        metrics = load_test.Metrics()
        with patch.object(load_test, 'request', side_effect=ConnectionRefusedError()):
            metrics.call('http://127.0.0.1:1', 'GET', '/api/community/users?perPage=20')
        result = metrics.summary(1)
        self.assertEqual(result['network_failures'], {'ConnectionRefusedError': 1})
        self.assertLess(result['methods']['GET']['p95_ms'], 1000)
        self.assertIn('GET users', result['operations'])

    def test_presence_and_message_latency_are_not_conflated(self):
        metrics = load_test.Metrics()
        with patch.object(load_test, 'request', side_effect=[(200, {}, 100, {}), (200, {}, 2000, {})]):
            metrics.call('http://127.0.0.1:1', 'POST', '/api/community/presence')
            metrics.call('http://127.0.0.1:1', 'POST', '/api/collections/messages/records')
        result = metrics.summary(1)
        self.assertEqual(result['operations']['POST presence']['p95_ms'], 100)
        self.assertEqual(result['operations']['POST messages']['p95_ms'], 2000)

    def test_nginx_transport_is_private_but_quotas_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            config = load_test.nginx_config(directory, 19095, 19096)
            self.assertIn('listen 127.0.0.1:19096;', config)
            self.assertNotIn('listen 443', config)
            self.assertIn('zone=retui_reads:1m rate=240r/m;', config)
            self.assertIn('limit_conn retui_stream_connections 10;', config)
            self.assertIn('real_ip_header X-Load-Test-IP;', config)
            self.assertIn('proxy_pass http://127.0.0.1:19095;', (directory / 'proxy.conf').read_text())
            self.assertNotIn('127.0.0.1:8095', (directory / 'proxy.conf').read_text())


if __name__ == '__main__':
    unittest.main(verbosity=2)
