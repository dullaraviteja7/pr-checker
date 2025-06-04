from flask import Blueprint, render_template, request, flash, redirect, url_for
from config.manager import AppConfig, ClusterConfig, GitAuthConfig, load_app_config, save_app_config
from typing import List, cast, Literal, Optional

bp = Blueprint('main', __name__)

@bp.route('/config', methods=['GET', 'POST'])
def configure():
    if request.method == 'POST':
        try:
            git_repo_url = request.form.get('git_repo_url')
            auth_method_form_value = request.form.get('auth_method')
            auth_method: Optional[Literal['pat', 'userpass']] = None
            if auth_method_form_value in ['pat', 'userpass']:
                auth_method = cast(Literal['pat', 'userpass'], auth_method_form_value)
            github_username = request.form.get('github_username') if auth_method == 'userpass' else None
            main_release_branch = request.form.get('main_release_branch')
            date_from = request.form.get('date_from') if request.form.get('date_from') else None
            date_to = request.form.get('date_to') if request.form.get('date_to') else None
            cluster_names = request.form.getlist('cluster_names')
            cluster_branches = request.form.getlist('cluster_branches')
            if not git_repo_url or not main_release_branch:
                flash('Git Repo URL and Main Release Branch are required.', 'error')
                current_config = load_app_config()
                return render_template('config.html', config=current_config), 400
            clusters: List[ClusterConfig] = []
            if len(cluster_names) != len(cluster_branches):
                flash('Mismatch between cluster names and branches count.', 'error')
                current_config = load_app_config()
                return render_template('config.html', config=current_config), 400
            has_at_least_one_cluster = False
            for i in range(len(cluster_names)):
                name = cluster_names[i].strip()
                branch = cluster_branches[i].strip()
                if name and branch:
                    clusters.append(ClusterConfig(name=name, release_branch=branch))
                    has_at_least_one_cluster = True
                elif name or branch:
                    flash(f'Cluster entry incomplete for "{name or branch}". Both name and branch are required.', 'error')
                    current_config = load_app_config()
                    return render_template('config.html', config=current_config), 400
            if not has_at_least_one_cluster:
                 flash('At least one complete cluster configuration is required.', 'error')
                 current_config = load_app_config()
                 return render_template('config.html', config=current_config), 400
            auth_config_data = GitAuthConfig(method=auth_method, username=github_username)
            new_config = AppConfig(
                git_repo_url=git_repo_url,
                auth_config=auth_config_data,
                main_release_branch=main_release_branch,
                clusters=clusters,
                date_range_from=date_from,
                date_range_to=date_to
            )
            save_app_config(new_config)
            flash('Configuration saved successfully!', 'success')
            if auth_method: flash('Remember to set your GITHUB_TOKEN or GITHUB_USERNAME/GITHUB_PASSWORD in the .env file in the project root if you haven\'t already.', 'info')
            return redirect(url_for('main.configure'))
        except Exception as e:
            flash(f'Error saving configuration: {str(e)}', 'error')
            print(f"Error during config save: {e}")
    current_config = load_app_config()
    return render_template('config.html', config=current_config)

@bp.route('/')
def index():
    return redirect(url_for('main.configure'))
