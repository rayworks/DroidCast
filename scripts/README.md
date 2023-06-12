# Note

The python script 'automation.py' written in Python 2.7.15 which is not
compatible with version 3.x. If you have Python 3.6+ installed, there
are a few steps to do code translation :

*   transform it into valid Python 3.x code by official library [2to3](https://docs.python.org/2/library/2to3.html)

    ```python
    2to3 -w automation.py
    ```

*   update the code at L35-L36 to :
    ```python
    p = subprocess.Popen([str(arg) for arg in args], stdout=out, encoding='utf-8')
    ```