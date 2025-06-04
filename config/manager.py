import json
import os
from typing import TypedDict, List, Optional, Literal
from dotenv import load_dotenv

load_dotenv()

class ClusterConfig(TypedDict):
    name: str
    release_branch: str

class GitAuthConfig(TypedDict):
    method: Optional[Literal['pat', 'userpass']]
    username: Optional[str]

class AppConfig(TypedDict):
    git_repo_url: Optional[str]
    auth_config: GitAuthConfig
    main_release_branch: Optional[str]
    clusters: List[ClusterConfig]
    date_range_from: Optional[str]
    date_range_to: Optional[str]

DEFAULT_CONFIG_PATH = 'data/config.json'
DEFAULT_ENV_PATH = '.env'

def load_app_config(filepath: str = DEFAULT_CONFIG_PATH) -> AppConfig:
    """Loads the application configuration from a JSON file."""
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
            config: AppConfig = {
                'git_repo_url': data.get('git_repo_url'),
                'auth_config': {
                    'method': data.get('auth_config', {}).get('method'),
                    'username': data.get('auth_config', {}).get('username')
                },
                'main_release_branch': data.get('main_release_branch'),
                'clusters': data.get('clusters', []),
                'date_range_from': data.get('date_range_from'),
                'date_range_to': data.get('date_range_to'),
            }
            return config
    except FileNotFoundError:
        return AppConfig(
            git_repo_url=None,
            auth_config=GitAuthConfig(method=None, username=None),
            main_release_branch=None,
            clusters=[],
            date_range_from=None,
            date_range_to=None
        )
    except json.JSONDecodeError:
        print(f"Warning: Could not decode JSON from {filepath}. Returning default config.")
        return AppConfig(
            git_repo_url=None,
            auth_config=GitAuthConfig(method=None, username=None),
            main_release_branch=None,
            clusters=[],
            date_range_from=None,
            date_range_to=None
        )

def save_app_config(config_data: AppConfig, filepath: str = DEFAULT_CONFIG_PATH) -> None:
    """Saves the application configuration to a JSON file."""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, 'w') as f:
        config_to_save = config_data.copy()
        if 'auth_config' in config_to_save and config_to_save['auth_config'] is not None:
            allowed_auth_keys = {'method', 'username'}
            config_to_save['auth_config'] = {
                k: v for k, v in config_to_save['auth_config'].items() if k in allowed_auth_keys
            }
        json.dump(config_to_save, f, indent=4)

def get_github_token() -> Optional[str]:
    """Retrieves the GitHub token from the GITHUB_TOKEN environment variable."""
    return os.getenv('GITHUB_TOKEN')

def get_github_userpass() -> Optional[tuple[str, str]]:
    """Retrieves GitHub username and password from environment variables."""
    user = os.getenv('GITHUB_USERNAME')
    password = os.getenv('GITHUB_PASSWORD')
    if user and password:
        return user, password
    return None

def ensure_env_gitignore(gitignore_path: str = '.gitignore', env_file_name: str = '.env'):
    try:
        with open(gitignore_path, 'r+') as f:
            if env_file_name not in f.read():
                f.write(f'''
# Environment variables file
{env_file_name}
''')
    except FileNotFoundError:
        with open(gitignore_path, 'w') as f:
            f.write(f'''# Environment variables file
{env_file_name}
''')

ensure_env_gitignore()
