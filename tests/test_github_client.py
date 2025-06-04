import unittest
from unittest.mock import patch, MagicMock, call
import os
import json
from typing import List, Optional, Dict, Any, cast, TypedDict
from datetime import datetime, timedelta
import hashlib # For checking cache key generation if needed

from github_helper.client import (
    construct_api_url, make_github_request, get_auth_headers, get_auth_tuple,
    GitHubAPIError, PRData, parse_pr_body_for_clusters, get_prs_in_date_range,
    is_commit_in_branch, get_pr_merge_commit_sha,
    load_prs_from_cache, save_prs_to_cache,
    load_cherry_pick_status_from_cache, save_cherry_pick_status_to_cache
)
from config.manager import save_app_config, AppConfig, GitAuthConfig, ClusterConfig, load_app_config

TEST_CONFIG_PATH_GH = 'data/test_gh_client_config.json'
TEST_PRS_CACHE_FILENAME = 'data/test_prs_cache.json'
TEST_CHERRY_PICK_CACHE_FILENAME = 'data/test_cherry_pick_cache.json'

class TestGitHubClient(unittest.TestCase):
    def setUp(self):
        self.original_environ = os.environ.copy()
        self.base_repo_url = "https://github.com/testowner/testrepo"
        default_auth = GitAuthConfig(method=None, username=None)
        default_config_data = AppConfig(git_repo_url=self.base_repo_url, auth_config=default_auth,
            main_release_branch="main", clusters=[],date_range_from=None,date_range_to=None)
        os.makedirs('data', exist_ok=True)
        save_app_config(default_config_data, TEST_CONFIG_PATH_GH)

        self.load_config_patcher = patch('github_helper.client.load_app_config', lambda: load_app_config(TEST_CONFIG_PATH_GH))
        self.mock_load_config = self.load_config_patcher.start()

        self.prs_cache_patcher = patch('github_helper.client.PRS_CACHE_FILE', TEST_PRS_CACHE_FILENAME)
        self.mock_prs_cache_path = self.prs_cache_patcher.start()
        self.cherry_pick_cache_patcher = patch('github_helper.client.CHERRY_PICK_CACHE_FILE', TEST_CHERRY_PICK_CACHE_FILENAME)
        self.mock_cherry_pick_cache_path = self.cherry_pick_cache_patcher.start()

        for f in [TEST_PRS_CACHE_FILENAME, TEST_CHERRY_PICK_CACHE_FILENAME]:
            if os.path.exists(f): os.remove(f)

    def tearDown(self):
        os.environ.clear(); os.environ.update(self.original_environ)
        for f in [TEST_CONFIG_PATH_GH, TEST_PRS_CACHE_FILENAME, TEST_CHERRY_PICK_CACHE_FILENAME]:
            if os.path.exists(f): os.remove(f)
        self.load_config_patcher.stop(); self.prs_cache_patcher.stop(); self.cherry_pick_cache_patcher.stop()

    def test_parse_pr_body_for_clusters_simple_case(self):
        body = "### Cluster Impact\n- [x] SMI\n- [X] SMN4"
        self.assertEqual(sorted(parse_pr_body_for_clusters(body)), sorted(["SMI", "SMN4"]))

    @patch('github_helper.client.make_github_request')
    def test_get_prs_in_date_range_with_caching(self, mock_make_request):
        cfg=self.mock_load_config(); repo_url=cast(str,cfg['git_repo_url'])
        main_b = "main_pr_cache"; d_from = "2023-03-01"; d_to = "2023-03-31"
        mock_pr_item = {"number":101,"title":"PR 101","body":"- [x] SMI","html_url":"url1","closed_at":"2023-03-15T00:00:00Z"}
        mock_make_request.return_value = ({"total_count":1,"items":[mock_pr_item]}, 200)
        prs = get_prs_in_date_range(repo_url, main_b, d_from, d_to)
        self.assertEqual(len(prs), 1); mock_make_request.assert_called_once()
        prs_cached = get_prs_in_date_range(repo_url, main_b, d_from, d_to)
        self.assertEqual(len(prs_cached), 1); mock_make_request.assert_called_once()

    @patch('github_helper.client.make_github_request')
    def test_is_commit_in_branch_with_caching(self, mock_make_request):
        cfg=self.mock_load_config(); repo_url=cast(str,cfg['git_repo_url'])
        branch, sha = "feature_cp_cache", "sha_cp1"
        mock_make_request.return_value = ({"status":"ahead"}, 200)
        self.assertTrue(is_commit_in_branch(repo_url, branch, sha)); mock_make_request.assert_called_once()
        mock_make_request.reset_mock()
        self.assertTrue(is_commit_in_branch(repo_url, branch, sha)); mock_make_request.assert_not_called()

    @patch('github_helper.client.make_github_request')
    def test_get_pr_merge_commit_sha_success(self, mock_make_request):
        mock_make_request.return_value = ({"merged": True, "merge_commit_sha": "abcdef123"}, 200)
        cfg=self.mock_load_config(); repo_url=cast(str,cfg['git_repo_url'])
        self.assertEqual(get_pr_merge_commit_sha(repo_url, 123), "abcdef123")

if __name__ == '__main__':
    unittest.main()
