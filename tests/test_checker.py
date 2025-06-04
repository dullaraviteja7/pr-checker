import unittest
from unittest.mock import patch, MagicMock, call
from typing import Dict, List, Optional, cast

from core.checker import analyze_prs, get_pr_details, AnalysisResult, PRCheckDetail
from config.manager import AppConfig, ClusterConfig, GitAuthConfig
from github_helper.client import PRData

def create_sample_app_config() -> AppConfig:
    return AppConfig(git_repo_url="https://github.com/testowner/testrepo",
        auth_config=GitAuthConfig(method='pat', username=None), main_release_branch="main",
        clusters=[ClusterConfig(name="smi", release_branch="release/smi"),
                  ClusterConfig(name="smc", release_branch="release/smc")],
        date_range_from="2023-01-01", date_range_to="2023-01-31")

class TestCoreChecker(unittest.TestCase):
    @patch('core.checker.get_prs_in_date_range')
    @patch('core.checker.get_pr_merge_commit_sha')
    @patch('core.checker.is_commit_in_branch')
    def test_analyze_prs_simple_case_one_missing(self, mock_is_commit, mock_get_sha, mock_get_search_prs):
        config = create_sample_app_config()
        pr101_search = PRData(number=101, title="PR101", body="### Cluster Impact\n- [x] smi\n- [x] smc",
                              target_clusters=["smi", "smc"], merge_commit_sha=None, html_url="url/101", merged_at="date")
        mock_get_search_prs.return_value = {101: pr101_search}
        mock_get_sha.return_value = "sha101" # Main branch merge SHA for PR101

        # Simulate PR101 is in smi branch, but not in smc branch
        def is_commit_se(repo_url, branch, sha):
            if sha=="sha101" and branch=="release/smi": return True
            if sha=="sha101" and branch=="release/smc": return False
            return False # Should not be called for other branches/shas in this test
        mock_is_commit.side_effect = is_commit_se

        result = analyze_prs(config)
        self.assertEqual(len(result['missing_prs_by_cluster']["smi"]), 0)
        self.assertEqual(len(result['missing_prs_by_cluster']["smc"]), 1)
        self.assertEqual(result['missing_prs_by_cluster']["smc"][0]['number'], 101)
        self.assertEqual(result['missing_prs_by_cluster']["smc"][0]['merge_commit_sha'], "sha101") # Check SHA was updated

    @patch('core.checker.get_pr_merge_commit_sha')
    @patch('core.checker.is_commit_in_branch')
    @patch('core.checker.get_prs_in_date_range')
    def test_get_pr_details_merged_and_cherry_picked(self, mock_get_search_prs, mock_is_commit, mock_get_sha_main):
        config = create_sample_app_config()
        pr_num_to_check = 201

        # Mock for get_pr_merge_commit_sha (main function being tested)
        mock_get_sha_main.return_value = "sha201_main"

        # Mock for get_prs_in_date_range (called by get_pr_details for PR body/title of merged PR)
        pr201_search_data = PRData(number=pr_num_to_check, title="Detail PR201", body="### Cluster Impact\n- [x] smi",
                                   target_clusters=["smi"], merge_commit_sha=None,
                                   html_url="url/201", merged_at="date")
        mock_get_search_prs.return_value = {pr_num_to_check: pr201_search_data}

        # Mock for is_commit_in_branch (cherry-pick checks)
        def is_commit_se(repo_url, branch, sha):
            if sha=="sha201_main" and branch=="release/smi": return True # Present in smi
            if sha=="sha201_main" and branch=="release/smc": return False # Missing in smc
            return False
        mock_is_commit.side_effect = is_commit_se

        details = get_pr_details(config, pr_num_to_check)
        self.assertIsNotNone(details)
        details = cast(PRCheckDetail, details)

        self.assertTrue(details['is_merged_to_main'])
        self.assertEqual(details['merge_commit_sha_main'], "sha201_main")
        self.assertEqual(details['marked_clusters'], ["smi"])
        self.assertTrue(details['cherry_pick_status_by_cluster']["smi"])
        self.assertFalse(details['cherry_pick_status_by_cluster']["smc"])
        self.assertEqual(details['pr_title'], "Detail PR201")
        self.assertEqual(details['pr_html_url'], "url/201")

if __name__ == '__main__':
    unittest.main()
