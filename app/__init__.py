from flask import Flask

def create_app():
    app = Flask(__name__)
    app.logger.setLevel('INFO') # Basic logging for info
    app.config['SECRET_KEY'] = 'your_very_secret_key_here'
    import os
    if not os.path.exists('data'):
        os.makedirs('data')
    from . import routes
    app.register_blueprint(routes.bp)
    return app
