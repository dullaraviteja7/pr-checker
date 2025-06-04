import unittest
import os
import json
from typing import cast
from config.manager import (
    AppConfig,
    GitAuthConfig,
    ClusterConfig,
    load_app_config,
    save_app_config,
    get_github_token,
    get_github_userpass,
    DEFAULT_CONFIG_PATH
)

class TestConfigManager(unittest.TestCase):

    def setUp(self):
        self.test_config_path = 'data/test_config.json'
        self.test_env_path = '.test_env'
        if os.path.exists(self.test_env_path): os.remove(self.test_env_path)
        if os.path.exists(self.test_config_path): os.remove(self.test_config_path)
        self.original_environ = os.environ.copy()

    def tearDown(self):
        if os.path.exists(self.test_config_path): os.remove(self.test_config_path)
        if os.path.exists(self.test_env_path): os.remove(self.test_env_path)
        os.environ.clear()
        os.environ.update(self.original_environ)

    def test_save_and_load_config(self):
        clusters_data: List[ClusterConfig] = [
            ClusterConfig(name='smi', release_branch='enh/smi-branch'),
            ClusterConfig(name='smc', release_branch='enh/smc-branch')
        ]
        auth_data: GitAuthConfig = GitAuthConfig(method='pat', username=None)
        config_data: AppConfig = AppConfig(
            git_repo_url='https://github.com/test/repo.git',
            auth_config=auth_data,
            main_release_branch='main',
            clusters=clusters_data,
            date_range_from='2023-01-01',
            date_range_to='2023-01-31'
        )
        save_app_config(config_data, self.test_config_path)
        loaded_config = load_app_config(self.test_config_path)
        self.assertEqual(loaded_config['git_repo_url'], 'https://github.com/test/repo.git')
        self.assertEqual(loaded_config['auth_config']['method'], 'pat')
        self.assertEqual(loaded_config['main_release_branch'], 'main')
        self.assertEqual(len(loaded_config['clusters']), 2)
        self.assertEqual(loaded_config['clusters'][0]['name'], 'smi')
        self.assertEqual(loaded_config['date_range_from'], '2023-01-01')

    def test_load_non_existent_config(self):
        loaded_config = load_app_config('data/non_existent_config.json')
        self.assertIsNone(loaded_config['git_repo_url'])
        self.assertIsNone(loaded_config['auth_config']['method'])
        self.assertEqual(loaded_config['clusters'], [])

    def test_load_corrupted_config(self):
        os.makedirs(os.path.dirname(self.test_config_path), exist_ok=True)
        with open(self.test_config_path, 'w') as f: f.write('this is not json')
        loaded_config = load_app_config(self.test_config_path)
        self.assertIsNone(loaded_config['git_repo_url'])
        self.assertEqual(loaded_config['clusters'], [])

    def test_save_config_omits_sensitive_auth(self):
        auth_data_with_password = cast(GitAuthConfig, {
            'method': 'userpass',
            'username': 'testuser',
            'password': 'WRONG_PASSWORD_SHOULD_NOT_BE_SAVED'
        })
        config_data: AppConfig = AppConfig(
            git_repo_url='https://github.com/test/repo.git',
            auth_config=auth_data_with_password, # type: ignore
            main_release_branch='main',
            clusters=[],
            date_range_from=None,
            date_range_to=None
        )
        save_app_config(config_data, self.test_config_path)
        with open(self.test_config_path, 'r') as f: saved_raw_config = json.load(f)
        self.assertIn('auth_config', saved_raw_config)
        self.assertIn('method', saved_raw_config['auth_config'])
        self.assertIn('username', saved_raw_config['auth_config'])
        self.assertNotIn('password', saved_raw_config['auth_config'])

    def test_get_github_token(self):
        if 'GITHUB_TOKEN' in os.environ: del os.environ['GITHUB_TOKEN']
        self.assertIsNone(get_github_token())
        os.environ['GITHUB_TOKEN'] = 'test_token_123'
        self.assertEqual(get_github_token(), 'test_token_123')

    def test_get_github_userpass(self):
        if 'GITHUB_USERNAME' in os.environ: del os.environ['GITHUB_USERNAME']
        if 'GITHUB_PASSWORD' in os.environ: del os.environ['GITHUB_PASSWORD']
        self.assertIsNone(get_github_userpass())
        os.environ['GITHUB_USERNAME'] = 'test_user'
        self.assertIsNone(get_github_userpass())
        if 'GITHUB_USERNAME' in os.environ: del os.environ['GITHUB_USERNAME']
        os.environ['GITHUB_USERNAME'] = 'test_user'
        os.environ['GITHUB_PASSWORD'] = 'test_pass'
        user_pass = get_github_userpass()
        self.assertIsNotNone(user_pass)
        if user_pass: self.assertEqual(user_pass[0], 'test_user'); self.assertEqual(user_pass[1], 'test_pass')

if __name__ == '__main__': unittest.main()
