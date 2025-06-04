import os
import shutil

def clear_cache():
    cache_files = [
        'data/prs_cache.json',
        'data/cherry_pick_cache.json'
    ]
    for file in cache_files:
        try:
            if os.path.exists(file):
                os.remove(file)
        except Exception as e:
            print(f"Error deleting cache file {file}: {e}")
