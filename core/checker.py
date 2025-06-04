from typing import Dict, List, Optional, TypedDict, cast
from config.manager import AppConfig
from github_helper.client import (
    PRData, get_prs_in_date_range, is_commit_in_branch, get_pr_merge_commit_sha,
    parse_pr_body_for_clusters # Correctly included in the import block
)

class MissingPRInfo(TypedDict):
    pr_number: int; pr_title: str; pr_url: str
    target_clusters_in_pr_body: List[str]

class AnalysisResult(TypedDict):
    missing_prs_by_cluster: Dict[str, List[PRData]]

class PRCheckDetail(TypedDict):
    pr_number: int; is_merged_to_main: bool; merge_commit_sha_main: Optional[str]
    marked_clusters: List[str]; cherry_pick_status_by_cluster: Dict[str, bool]
    pr_title: Optional[str]; pr_html_url: Optional[str]

def analyze_prs(app_config: AppConfig) -> AnalysisResult:
    if not app_config['git_repo_url'] or not app_config['main_release_branch']:
        return AnalysisResult(missing_prs_by_cluster={})

    # Ensure git_repo_url and main_release_branch are not None before proceeding
    repo_url = cast(str, app_config['git_repo_url'])
    main_branch = cast(str, app_config['main_release_branch'])

    fetched_prs: Dict[int, PRData] = get_prs_in_date_range(
        repo_url=repo_url, main_branch=main_branch,
        from_date=app_config.get('date_range_from'), to_date=app_config.get('date_range_to'))

    missing_report: Dict[str, List[PRData]] = {c['name']: [] for c in app_config.get('clusters',[])}

    for pr_num, pr_search_data in fetched_prs.items():
        actual_sha = get_pr_merge_commit_sha(repo_url, pr_num)
        if not actual_sha:
            print(f"Skipping PR {pr_num} as its merge SHA couldn't be fetched.")
            continue

        pr_data_for_report = pr_search_data.copy()
        pr_data_for_report['merge_commit_sha'] = actual_sha

        for target_cluster_name in pr_search_data['target_clusters']:
            for conf_cluster in app_config.get('clusters', []):
                if conf_cluster['name'].lower() == target_cluster_name.lower():
                    is_present = is_commit_in_branch(repo_url,
                        conf_cluster['release_branch'], actual_sha)
                    if not is_present:
                        missing_report[conf_cluster['name']].append(pr_data_for_report)
                    break
    return AnalysisResult(missing_prs_by_cluster=missing_report)

def get_pr_details(app_config: AppConfig, pr_number: int) -> Optional[PRCheckDetail]:
    if not app_config['git_repo_url']: return None
    repo_url = cast(str, app_config['git_repo_url'])
    main_release_branch = cast(str, app_config.get('main_release_branch')) # Ensure it's str for get_prs_in_date_range

    main_merge_sha = get_pr_merge_commit_sha(repo_url, pr_number)
    is_merged_main = main_merge_sha is not None

    pr_body_to_parse = f"Body for PR {pr_number} not found via main branch search."
    pr_title_to_show = f"Title for PR {pr_number} not found"
    pr_html_url_to_show = f"URL for PR {pr_number} not found"

    if is_merged_main and main_release_branch:
        all_prs_main = get_prs_in_date_range(repo_url, main_release_branch, None, None)
        if pr_number in all_prs_main:
            pr_data = all_prs_main[pr_number]
            pr_body_to_parse = pr_data['body']
            pr_title_to_show = pr_data['title']
            pr_html_url_to_show = pr_data['html_url']
    elif not is_merged_main: # PR not merged to main, details might not be in main branch search
        print(f"PR {pr_number} not found as merged into main branch. Details might be limited or inaccurate if not fetched directly.")
        # Future: Consider a direct PR fetch: get_pr_by_number(repo_url, pr_number) -> Optional[PRData]
        # For now, we'll use the fallback/simulated data for body/title/url.

    marked_clusters = parse_pr_body_for_clusters(pr_body_to_parse)
    cherry_pick_statuses: Dict[str, bool] = {}
    if main_merge_sha:
        for cluster_info in app_config.get('clusters', []):
            status = is_commit_in_branch(repo_url,
                cluster_info['release_branch'], main_merge_sha)
            cherry_pick_statuses[cluster_info['name']] = status

    return PRCheckDetail(pr_number=pr_number, is_merged_to_main=is_merged_main,
        merge_commit_sha_main=main_merge_sha, marked_clusters=marked_clusters,
        cherry_pick_status_by_cluster=cherry_pick_statuses,
        pr_title=pr_title_to_show, pr_html_url=pr_html_url_to_show)
