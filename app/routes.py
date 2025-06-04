from flask import Blueprint, render_template, request, flash, redirect, url_for, current_app
from config.manager import AppConfig, ClusterConfig, GitAuthConfig, load_app_config, save_app_config
from core.checker import analyze_prs, AnalysisResult, PRData, PRCheckDetail # Added PRCheckDetail
from typing import List, cast, Dict, Optional, Literal
from datetime import datetime
from app.clear_cache import clear_cache
from flask import jsonify

bp = Blueprint('main', __name__)

@bp.route('/')
def index():
    return redirect(url_for('main.dashboard'))

@bp.route('/config', methods=['GET', 'POST'])
def configure():
    if request.method == 'POST':
        try:
            git_repo_url = request.form.get('git_repo_url')
            auth_method_form_value = request.form.get('auth_method')
            auth_method_casted: Optional[Literal['pat', 'userpass']] = None
            if auth_method_form_value in ['pat', 'userpass']:
                auth_method_casted = cast(Literal['pat', 'userpass'], auth_method_form_value)

            github_username = request.form.get('github_username') if auth_method_casted == 'userpass' else None
            main_release_branch = request.form.get('main_release_branch')
            date_from_form = request.form.get('date_from', '')
            date_to_form = request.form.get('date_to', '')
            date_from = date_from_form if date_from_form else None
            date_to = date_to_form if date_to_form else None
            cluster_names = request.form.getlist('cluster_names')
            cluster_branches = request.form.getlist('cluster_branches')

            if not git_repo_url or not main_release_branch:
                flash('Git Repo URL and Main Release Branch are required.', 'error')
                current_config_on_error = AppConfig(git_repo_url=git_repo_url,
                    auth_config=GitAuthConfig(method=auth_method_casted, username=github_username),
                    main_release_branch=main_release_branch,
                    clusters=[ClusterConfig(name=cn, release_branch=cb) for cn, cb in zip(cluster_names, cluster_branches)],
                    date_range_from=date_from, date_range_to=date_to)
                return render_template('config.html', config=current_config_on_error), 400

            clusters: List[ClusterConfig] = []
            if len(cluster_names) != len(cluster_branches):
                flash('Mismatch cluster names/branches.', 'error'); return render_template('config.html', config=load_app_config()), 400
            has_at_least_one_cluster = False
            for i in range(len(cluster_names)):
                name = cluster_names[i].strip(); branch = cluster_branches[i].strip()
                if name and branch: clusters.append(ClusterConfig(name=name, release_branch=branch)); has_at_least_one_cluster = True
                elif name or branch: flash(f'Incomplete cluster entry "{name or branch}".','error'); return render_template('config.html', config=load_app_config()), 400
            if not has_at_least_one_cluster: flash('At least one cluster required.','error'); return render_template('config.html', config=load_app_config()), 400

            auth_config_data = GitAuthConfig(method=auth_method_casted, username=github_username)
            new_config = AppConfig(git_repo_url=git_repo_url, auth_config=auth_config_data, main_release_branch=main_release_branch,
                                   clusters=clusters, date_range_from=date_from, date_range_to=date_to)
            save_app_config(new_config)
            flash('Configuration saved!', 'success')
            if auth_method_casted: flash('Remember to set GITHUB_TOKEN or GITHUB_USERNAME/PASSWORD in .env.', 'info')
            return redirect(url_for('main.configure'))
        except Exception as e:
            flash(f'Error saving config: {e}', 'error'); current_app.logger.error(f"Config save error: {e}", exc_info=True)
            return render_template('config.html', config=load_app_config()), 500
    return render_template('config.html', config=load_app_config())

@bp.route('/dashboard', methods=['GET'])
def dashboard():
    app_config = load_app_config()
    if not app_config.get('git_repo_url') or not app_config.get('main_release_branch'):
        flash('Application not configured. Please configure first.', 'warning'); return redirect(url_for('main.configure'))
    filter_date_from_str = request.args.get('date_from', app_config.get('date_range_from') or '')
    filter_date_to_str = request.args.get('date_to', app_config.get('date_range_to') or '')
    filter_cluster_name = request.args.get('filter_cluster_name', '')
    analysis_config = app_config.copy()
    analysis_config['date_range_from'] = filter_date_from_str if filter_date_from_str else None
    analysis_config['date_range_to'] = filter_date_to_str if filter_date_to_str else None
    analysis_results: AnalysisResult = AnalysisResult(missing_prs_by_cluster={})
    try:
        analysis_results = analyze_prs(analysis_config)
        current_app.logger.info(f"Dashboard analysis: {len(analysis_results['missing_prs_by_cluster'])} clusters with missing PRs.")
    except Exception as e:
        current_app.logger.error(f"Dashboard analysis error: {e}", exc_info=True); flash(f"Error analyzing PRs: {e}", "error")
    display_data: Dict[str, List[PRData]] = {}
    if filter_cluster_name:
        display_data[filter_cluster_name] = analysis_results['missing_prs_by_cluster'].get(filter_cluster_name, [])
    else: display_data = analysis_results['missing_prs_by_cluster']
    configured_cluster_names = [c['name'] for c in app_config.get('clusters', [])]
    return render_template('dashboard.html', results=display_data, configured_clusters=configured_cluster_names,
                           current_filters={'date_from': filter_date_from_str, 'date_to': filter_date_to_str, 'cluster_name': filter_cluster_name},
                           app_config=app_config)

@bp.route('/check_pr', methods=['GET', 'POST'])
def check_pr_page():
    app_config = load_app_config()
    if not app_config.get('git_repo_url'):
        flash('Application is not configured yet. Please configure it first.', 'warning')
        return redirect(url_for('main.configure'))

    pr_details: Optional[PRCheckDetail] = None
    pr_number_input: Optional[str] = None

    if request.method == 'POST':
        pr_number_str = request.form.get('pr_number')
        pr_number_input = pr_number_str

        if pr_number_str and pr_number_str.isdigit():
            pr_num = int(pr_number_str)
            try:
                from core.checker import get_pr_details # Keep import here to ensure it's found
                details_result = get_pr_details(app_config, pr_num)
                if details_result:
                    pr_details = details_result
                else:
                    flash(f"Could not retrieve details for PR #{pr_num}. It might not exist or an error occurred.", "error")
            except Exception as e:
                current_app.logger.error(f"Error getting PR details for #{pr_num}: {e}", exc_info=True)
                flash(f"An unexpected error occurred while fetching details for PR #{pr_num}: {str(e)}", "error")
        elif pr_number_str:
            flash("Invalid PR number format. Please enter a number.", "error")
        else:
            flash("Please enter a PR number.", "warning")

    return render_template('pr_checker.html',
                           details=pr_details,
                           pr_number_input=pr_number_input,
                           configured_clusters=app_config.get('clusters', []),
                           app_config=app_config)

@bp.route('/clear_cache', methods=['POST'])
def clear_cache_route():
    try:
        clear_cache()
        flash('Cache cleared successfully!', 'success')
    except Exception as e:
        flash(f'Error clearing cache: {e}', 'error')
    return redirect(url_for('main.dashboard'))
