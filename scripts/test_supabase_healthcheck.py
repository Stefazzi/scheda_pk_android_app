import io
import unittest
from contextlib import redirect_stderr
from email.message import Message
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError

import supabase_healthcheck as health


URL = "https://abcdefghijklmnopqrst.supabase.co"
KEY = "sb_publishable_fake_key_for_tests"


def response(body=b"true", status=200, content_type="application/json"):
    result = Mock()
    result.status = status
    result.headers = Message()
    result.headers["Content-Type"] = content_type
    result.read.return_value = body
    result.__enter__ = Mock(return_value=result)
    result.__exit__ = Mock(return_value=False)
    return result


class HealthcheckTests(unittest.TestCase):
    def test_configuration(self):
        self.assertEqual(health.configuration({"SUPABASE_URL": URL + "/", "SUPABASE_PUBLISHABLE_KEY": KEY}), (URL, KEY))

    def test_reject_unsafe_urls(self):
        for url in ("", "http://abcdefghijklmnopqrst.supabase.co", URL + "?x=1", URL + "/rest/v1", URL + ".evil.test", "https://user:pass@abcdefghijklmnopqrst.supabase.co", "https://supabase.com/dashboard"):
            with self.subTest(url=url), self.assertRaises(health.HealthcheckError):
                health.configuration({"SUPABASE_URL": url, "SUPABASE_PUBLISHABLE_KEY": KEY})

    def test_reject_privileged_or_malformed_keys(self):
        for key in ("", "sb_secret_fake", "eyJhbGciOiJIUzI1NiJ9.fake.fake", "service_role", "sb_publishable_x\nInjected: y"):
            with self.subTest(key=key), self.assertRaises(health.HealthcheckError):
                health.configuration({"SUPABASE_URL": URL, "SUPABASE_PUBLISHABLE_KEY": key})

    def test_success_read_only_request(self):
        opened = response()
        opener, sleep = Mock(), Mock()
        opener.open.return_value = opened
        health.check(URL, KEY, opener=opener, sleep=sleep)
        request = opener.open.call_args.args[0]
        self.assertEqual(request.get_method(), "GET")
        self.assertEqual(request.full_url, URL + "/rest/v1/rpc/pokerole_healthcheck")
        self.assertEqual(request.get_header("Apikey"), KEY)
        self.assertIsNone(request.get_header("Authorization"))
        self.assertIsNone(request.data)
        self.assertEqual(opener.open.call_args.kwargs["timeout"], 20)
        opened.read.assert_called_once_with(1025)
        sleep.assert_not_called()

    def test_reject_false_positives(self):
        for body in (b"false", b"1", b'"true"', b"[]", b"{}", b"null", b"", b"<html>login</html>", b"\xff", b"x" * 1025):
            with self.subTest(body=body[:20]), self.assertRaises(health.HealthcheckError):
                health.check(URL, KEY, opener=Mock(open=Mock(return_value=response(body))))

    def test_reject_html_content_type(self):
        with self.assertRaises(health.HealthcheckError):
            health.check(URL, KEY, opener=Mock(open=Mock(return_value=response(content_type="text/html"))))

    def test_reject_unexpected_success_status(self):
        with self.assertRaises(health.HealthcheckError):
            health.check(URL, KEY, opener=Mock(open=Mock(return_value=response(status=204))))

    def test_retry_temporary_http_errors(self):
        for status in (408, 429, 500, 503, 504):
            with self.subTest(status=status):
                opener, sleep = Mock(), Mock()
                opener.open.side_effect = [HTTPError(URL, status, "private", {}, None), response()]
                health.check(URL, KEY, opener=opener, sleep=sleep)
                self.assertEqual(opener.open.call_count, 2)
                sleep.assert_called_once_with(2)

    def test_fail_fast_on_configuration_errors_and_redirects(self):
        for status in (301, 302, 307, 308, 400, 401, 403, 404):
            with self.subTest(status=status), self.assertRaises(health.HealthcheckError) as caught:
                opener = Mock(open=Mock(side_effect=HTTPError(URL, status, KEY, {}, None)))
                health.check(URL, KEY, opener=opener, sleep=Mock())
            self.assertEqual(opener.open.call_count, 1)
            self.assertNotIn(KEY, str(caught.exception))

    def test_network_retry_limit_and_no_error_leak(self):
        opener, sleep = Mock(), Mock()
        opener.open.side_effect = URLError(KEY)
        with self.assertRaises(health.HealthcheckError) as caught:
            health.check(URL, KEY, opener=opener, sleep=sleep)
        self.assertEqual(opener.open.call_count, 3)
        self.assertEqual([call.args[0] for call in sleep.call_args_list], [2, 5])
        self.assertNotIn(KEY, str(caught.exception))

    def test_redirect_handler_never_forwards_key(self):
        self.assertIsNone(health.NoRedirects().redirect_request(None, None, 302, "", {}, "https://evil.test"))

    def test_main_hides_unexpected_exceptions(self):
        output = io.StringIO()
        with patch.object(health, "configuration", side_effect=RuntimeError(KEY)), redirect_stderr(output):
            self.assertEqual(health.main(), 1)
        self.assertNotIn(KEY, output.getvalue())


if __name__ == "__main__":
    unittest.main()
