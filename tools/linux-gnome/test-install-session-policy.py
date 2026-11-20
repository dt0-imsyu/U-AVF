"""Exercise real embedded session selectors without a VM or host account changes."""
import ast
from pathlib import Path
import types
import unittest
from unittest.mock import Mock

GUEST = Path(__file__).parent / 'guest'

class Executed(Exception):
    pass

class PolicyTests(unittest.TestCase):
    def audio(self, uid):
        source = (GUEST/'uavf-install-runtime.sh').read_text()
        code = source.split("<<'AUDIO_SESSION_PY'", 1)[1].split('\nAUDIO_SESSION_PY', 1)[0]
        # Execute the actual selector after its imports, with isolated mocks.
        tree = ast.parse(code)
        tree.body = [node for node in tree.body if not isinstance(node, (ast.Import, ast.ImportFrom))]
        calls = []
        env = {'XDG_RUNTIME_DIR': '/run/user/%s' % uid, 'DISPLAY': ':0'}
        helper = types.SimpleNamespace(find_display=lambda: env.copy())
        loader = types.SimpleNamespace(exec_module=lambda module: None)
        util = types.SimpleNamespace(spec_from_file_location=lambda *args: types.SimpleNamespace(loader=loader),
                                     module_from_spec=lambda spec: helper)
        os = types.SimpleNamespace(
            initgroups=lambda *args: calls.append(('groups', *args)),
            setgid=lambda gid: calls.append(('gid', gid)),
            setuid=lambda uid: calls.append(('uid', uid)))
        def execute(*args):
            calls.append(('exec', *args))
            raise Executed()
        os.execve = execute
        ticks = iter([0, 0, 181])
        context = dict(importlib=types.SimpleNamespace(util=util), os=os,
                       pwd=types.SimpleNamespace(getpwuid=lambda uid: types.SimpleNamespace(
                           pw_name='chosen_user', pw_gid=1001, pw_dir='/home/chosen_user')),
                       time=types.SimpleNamespace(monotonic=lambda: next(ticks), sleep=lambda _: None))
        with self.assertRaises(Executed if uid >= 1000 else RuntimeError):
            exec(compile(tree, '<embedded audio selector>', 'exec'), context)
        return calls

    def test_custom_audio_user_drops_privileges(self):
        calls = self.audio(1001)
        self.assertEqual(calls[:3], [('groups', 'chosen_user', 1001), ('gid', 1001), ('uid', 1001)])
        self.assertEqual(calls[3][3]['HOME'], '/home/chosen_user')
        self.assertEqual(calls[3][3]['PULSE_SERVER'], 'unix:/run/user/1001/pulse/native')

    def test_audio_does_not_capture_greeter(self):
        self.assertEqual(self.audio(121), [])

    def test_install_preserves_login_choice(self):
        text = (GUEST/'uavf-install-runtime.sh').read_text()
        self.assertNotIn('AutomaticLoginEnable=true', text)
        self.assertNotIn('AutomaticLogin=uavf', text)
        self.assertNotIn('User=uavf', text)
        self.assertIn('account_policy=installer', text)

    def test_clipboard_rejects_greeter(self):
        text = (GUEST/'uavf-clipboard.py').read_text()
        self.assertIn("if not 1000 <= uid < 60000:", text)
        ast.parse(text)

    def test_encoded_accepts_only_gdm_or_normal_user(self):
        tree = ast.parse((GUEST/'uavf-encoded-display.py').read_text())
        outer = next(node for node in tree.body if isinstance(node, ast.If)
                     and isinstance(node.test, ast.Compare))
        loop = next(node for node in outer.body if isinstance(node, ast.For))
        select = next(node for node in loop.body if isinstance(node, ast.If))
        for uid, name, accepted in [(121, 'gdm', True), (1001, 'chosen_user', True),
                                    (0, 'root', False), (120, 'other-service', False)]:
            code = ast.Module(body=[ast.For(target=ast.Name(id='attempt', ctx=ast.Store()),
                                          iter=ast.List(elts=[ast.Constant(0)], ctx=ast.Load()),
                                          body=[select, ast.Assign(targets=[ast.Name(id='env', ctx=ast.Store())],
                                                                  value=ast.Constant(None))], orelse=[])], type_ignores=[])
            context = dict(env={'XDG_RUNTIME_DIR': '/run/user/%s' % uid},
                           pwd=types.SimpleNamespace(getpwuid=lambda _: types.SimpleNamespace(pw_name=name)))
            exec(compile(ast.fix_missing_locations(code), '<encoded selector>', 'exec'), context)
            self.assertEqual(context['env'] is not None, accepted)

if __name__ == '__main__':
    unittest.main()
