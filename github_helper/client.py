import requests
import os
import re
import json
from datetime import datetime, timedelta
import hashlib
from typing import Optional, Dict, Any, Tuple, List, Literal, TypedDict, cast
from urllib.parse import urljoin

from config.manager import load_app_config, get_github_token, get_github_userpass

GITHUB_API_URL = "https://api.github.com/"

class PRData(TypedDict):
    number: int
    title: str
    body: str
    merge_commit_sha: Optional[str]
    target_clusters: List[str]
    html_url: str
    merged_at: Optional[str]

class GitHubAPIError(Exception):
    def __init__(self, message, status_code=None):
        super().__init__(message)
        self.status_code = status_code

def parse_pr_body_for_clusters(body: Optional[str]) -> List[str]:
    if not body: return []
    clusters = []
    match = re.search(r"###\s+Cluster Impact(.*?)(?:###\s+|$)", body, re.DOTALL | re.IGNORECASE)
    if not match: return []
    section_content = match.group(1)
    pattern = re.compile(r"^\s*[-*+]\s+\[[xX]\]\s+([A-Za-z0-9_.-]+).*$", re.MULTILINE)
    for m in pattern.finditer(section_content): clusters.append(m.group(1).strip())
    return list(set(clusters))

CACHE_DIR = "data"
PRS_CACHE_FILE = os.path.join(CACHE_DIR, "prs_cache.json")
CHERRY_PICK_CACHE_FILE = os.path.join(CACHE_DIR, "cherry_pick_cache.json")
CACHE_DURATION_HOURS = 4

def _load_cache_data(file_path: str, cache_key: str) -> Optional[Any]:
    if not os.path.exists(file_path): return None
    try:
        with open(file_path, 'r') as f: full_cache = json.load(f)
        cached_item = full_cache.get(cache_key)
        if not cached_item: return None
        timestamp_str = cached_item.get("timestamp")
        data = cached_item.get("data")
        if not timestamp_str or data is None: return None
        timestamp = datetime.fromisoformat(timestamp_str)
        if datetime.utcnow() - timestamp > timedelta(hours=CACHE_DURATION_HOURS):
            print(f"Cache for key {cache_key} in {file_path} is stale.")
            return None
        return data
    except Exception as e:
        print(f"Error loading from cache file {file_path}, key {cache_key}: {e}")
        return None

def _save_cache_data(file_path: str, cache_key: str, data_to_cache: Any):
    os.makedirs(CACHE_DIR, exist_ok=True); full_cache: Dict[str, Any] = {}
    if os.path.exists(file_path):
        try:
            with open(file_path, 'r') as f: content = f.read()
            if content.strip(): full_cache = json.loads(content)
        except Exception as e: print(f"Could not read cache file {file_path}, will overwrite: {e}")
    full_cache[cache_key] = {"timestamp": datetime.utcnow().isoformat(), "data": data_to_cache}
    try:
        with open(file_path, 'w') as f: json.dump(full_cache, f, indent=4)
    except Exception as e: print(f"Error saving to cache file {file_path} for key {cache_key}: {e}")

def load_prs_from_cache(query_key: str) -> Optional[Dict[int, PRData]]:
    cached_data = _load_cache_data(PRS_CACHE_FILE, query_key)
    if cached_data:
        loaded_prs: Dict[int, PRData] = {}
        for k_str, v_raw in cached_data.items(): # Assuming cached_data is Dict[str, Dict] from save
            if isinstance(v_raw, dict):
                loaded_prs[int(k_str)] = cast(PRData, v_raw)
            else:
                print(f"Warning: Malformed PR data in cache for key {query_key}, item key {k_str}")
        return loaded_prs
    return None

def save_prs_to_cache(query_key: str, prs_dict: Dict[int, PRData]):
    prs_to_save = {str(k): v for k, v in prs_dict.items()} # Ensure keys are strings for JSON
    _save_cache_data(PRS_CACHE_FILE, query_key, prs_to_save)

def load_cherry_pick_status_from_cache(branch_name: str, commit_sha: str) -> Optional[bool]:
    return cast(Optional[bool], _load_cache_data(CHERRY_PICK_CACHE_FILE, f"{branch_name}::{commit_sha}"))

def save_cherry_pick_status_to_cache(branch_name: str, commit_sha: str, status: bool):
    _save_cache_data(CHERRY_PICK_CACHE_FILE, f"{branch_name}::{commit_sha}", status)

def get_auth_headers():
    config = load_app_config(); headers = {"Accept": "application/vnd.github.v3+json"}
    auth_config_dict = config.get('auth_config', {})
    auth_method = auth_config_dict.get('method')
    if auth_method == 'pat':
        token = get_github_token()
        if token: headers["Authorization"] = f"token {token}"
        else: print("Warning: PAT auth configured, GITHUB_TOKEN not found.")
    return headers
def get_auth_tuple():
    config = load_app_config(); auth_config_dict = config.get('auth_config', {})
    auth_method = auth_config_dict.get('method')
    if auth_method == 'userpass':
        creds = get_github_userpass()
        if not creds: print("Warning: User/Pass auth configured, credentials not found.")
        return creds
    return None
def construct_api_url(repo_url, endpoint):
    if not repo_url: return None
    try:
        path_parts = repo_url.strip('/').split('/'); owner = path_parts[-2]; repo_name = path_parts[-1].replace('.git', '')
        base_api_path = f"repos/{owner}/{repo_name}/"
        return urljoin(urljoin(GITHUB_API_URL, base_api_path), endpoint.lstrip('/'))
    except IndexError: print(f"Error parsing URL: {repo_url}"); return None
def make_github_request(method, url, params=None, json_data=None):
    headers = get_auth_headers(); auth_tuple = get_auth_tuple(); response: Optional[requests.Response] = None
    try:
        response = requests.request(method, url, headers=headers, auth=auth_tuple, params=params, json=json_data, timeout=10)
        if response.status_code == 401: raise GitHubAPIError("Unauthorized", 401)
        if response.status_code == 403:
            if response.headers.get('X-RateLimit-Remaining')=='0': raise GitHubAPIError("Rate limit", 403)
            raise GitHubAPIError("Forbidden", 403)
        if response.status_code == 404: raise GitHubAPIError("Not Found", 404)
        response.raise_for_status()
        return (None, 204) if response.status_code == 204 else (response.json(), response.status_code)
    except requests.exceptions.Timeout as e: raise GitHubAPIError(f"Timeout: {method} {url}. Error: {e}", None)
    except requests.exceptions.RequestException as e: raise GitHubAPIError(f"Request error: {method} {url}. Error: {e}", None)
    except json.JSONDecodeError as e:
        status_code = response.status_code if response is not None else None
        text = response.text if response is not None else "N/A"
        raise GitHubAPIError(f"JSON decode error: {e}. Status: {status_code}. Text: {text}", status_code)

def get_prs_in_date_range(repo_url: str, main_branch: str, from_date: Optional[str], to_date: Optional[str]) -> Dict[int, PRData]:
    if not repo_url or not main_branch: raise ValueError("Repo URL and main branch needed.")
    path_parts = repo_url.strip('/').split('/'); owner = path_parts[-2]; repo_name = path_parts[-1].replace('.git', '')
    query_parts = [f"repo:{owner}/{repo_name}", "is:pr", "is:merged", f"base:{main_branch}"]
    merged_date_filter = ""
    if from_date and to_date: merged_date_filter = f"merged:{from_date}..{to_date}"
    elif from_date: merged_date_filter = f"merged:>={from_date}"
    elif to_date: merged_date_filter = f"merged:<={to_date}"
    if merged_date_filter: query_parts.append(merged_date_filter)
    query = " ".join(query_parts)
    query_cache_key = hashlib.md5(query.encode()).hexdigest()
    cached_prs = load_prs_from_cache(query_cache_key)
    if cached_prs is not None: print(f"Loaded {len(cached_prs)} PRs from cache for query: {query}"); return cached_prs
    search_url = urljoin(GITHUB_API_URL, "search/issues"); all_pr_items: List[Dict[str, Any]] = []; page = 1
    print(f"Fetching PRs from API with query: {query}"); per_page = 100
    while True:
        params = {"q": query, "sort": "merged", "order": "desc", "per_page": str(per_page), "page": str(page)}
        try:
            response_data, status_code = make_github_request("GET", search_url, params=params)
            if status_code == 200 and response_data:
                items = response_data.get("items", []) ; all_pr_items.extend(items)
                if not items or len(items) < per_page or len(all_pr_items) >= 1000:
                    if len(all_pr_items) >= 1000 and response_data.get("total_count", 0) > 1000: print(f"Warning: Fetched 1000 PRs, but total is {response_data.get('total_count',0)}.")
                    break
                page += 1
            else: print(f"Failed to fetch PRs (page {page}) query '{query}'. Status: {status_code}"); break
        except GitHubAPIError as e: print(f"API error during PR fetch (page {page}): {e}"); break
    processed_prs: Dict[int, PRData] = {}
    for item in all_pr_items:
        pr_number = item.get("number")
        if not pr_number: continue
        processed_prs[pr_number] = PRData(number=pr_number, title=item.get("title", "N/A"), body=item.get("body", ""),
            target_clusters=parse_pr_body_for_clusters(item.get("body")), merge_commit_sha=None,
            html_url=item.get("html_url", ""), merged_at=item.get('closed_at'))
    if processed_prs: save_prs_to_cache(query_cache_key, processed_prs)
    return processed_prs

def is_commit_in_branch(repo_url: str, branch_name: str, commit_sha: str) -> bool:
    if not all([repo_url, branch_name, commit_sha]): print("Error: Missing args for is_commit_in_branch."); return False
    cached_status = load_cherry_pick_status_from_cache(branch_name, commit_sha)
    if cached_status is not None: print(f"Cache hit for CP status of {commit_sha[:7]} in {branch_name}: {cached_status}"); return cached_status
    print(f"API check for CP status of {commit_sha[:7]} in {branch_name}..."); compare_endpoint = f"compare/{commit_sha}...{branch_name}"
    api_url = construct_api_url(repo_url, compare_endpoint)
    if not api_url: return False
    try:
        response_data, status_code = make_github_request("GET", api_url)
        if status_code == 200 and response_data:
            status = response_data.get("status")
            result = status in ['ahead', 'identical']
            save_cherry_pick_status_to_cache(branch_name, commit_sha, result); return result
        print(f"Compare API unexpected status {status_code} for {commit_sha}...{branch_name}. Resp: {response_data}"); return False
    except GitHubAPIError as e:
        if e.status_code in [404, 422]: print(f"API Error (ref not found/compare too large) for {commit_sha}...{branch_name}: {e}"); save_cherry_pick_status_to_cache(branch_name, commit_sha, False); return False
        print(f"API Error comparing {commit_sha}...{branch_name}: {e}"); return False

def get_pr_merge_commit_sha(repo_url: str, pr_number: int) -> Optional[str]:
    if not repo_url or not pr_number: return None
    pr_endpoint = f"pulls/{pr_number}"; api_url = construct_api_url(repo_url, pr_endpoint)
    if not api_url: return None
    try:
        response_data, status_code = make_github_request("GET", api_url)
        if status_code == 200 and response_data:
            if response_data.get("merged") and response_data.get("merge_commit_sha"):
                return cast(str, response_data["merge_commit_sha"])
            else: print(f"PR {pr_number} not merged or merge_commit_sha missing. Merged: {response_data.get('merged')}"); return None
        return None
    except GitHubAPIError as e: print(f"API Error fetching PR {pr_number} details: {e}"); return None
