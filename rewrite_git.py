import os

cmd = """git filter-branch -f --env-filter '
if [ "$GIT_AUTHOR_EMAIL" = "Devil1716@users.noreply.github.com" ]; then
    export GIT_AUTHOR_EMAIL="sharan071718@gmail.com"
    export GIT_AUTHOR_NAME="Devil1716"
fi
if [ "$GIT_COMMITTER_EMAIL" = "Devil1716@users.noreply.github.com" ]; then
    export GIT_COMMITTER_EMAIL="sharan071718@gmail.com"
    export GIT_COMMITTER_NAME="Devil1716"
fi
' -- --all
"""

os.environ["FILTER_BRANCH_SQUELCH_WARNING"] = "1"
os.system(cmd)
