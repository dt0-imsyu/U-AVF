"""Validate actual embedded WPR profile without starting tracing or a VM."""
import ast
import re
import subprocess
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

folder=Path(__file__).parent
header=(folder/'windows-cpu-profile.h').read_text()
definition=header.split('static const char cpu_profile_xml[]=')[1].split(';\n',1)[0]
xml=''.join(ast.literal_eval(literal) for literal in re.findall(r'"(?:[^"\\]|\\.)*"',definition))
embedded=ET.fromstring(xml)
asset=ET.parse(folder/'windows-cpu-sample.wprp').getroot()
def structure(element):return element.tag,element.attrib,[structure(child) for child in element]
assert structure(embedded)==structure(asset), 'Embedded profile differs from validated asset'
assert [node.attrib['Value'] for node in embedded.findall('.//Keyword')]==['ProcessThread','Loader','SampledProfile']
assert not embedded.findall('.//Stacks') and not embedded.findall('.//EventProvider')
assert embedded.find('.//Profile').attrib['LoggingMode']=='Memory'
collector=embedded.find('.//SystemCollector')
assert int(collector.find('BufferSize').attrib['Value'])*int(collector.find('Buffers').attrib['Value'])==4096
assert '-filemode' not in header
assert '-cancel -instancename UAVFCPU%lu' in header
assert 'cpu-sample-%lu.etl' in header and 'CREATE_NEW' in header
with tempfile.TemporaryDirectory(prefix='uavf-cpu-profile-') as scratch:
    profile=Path(scratch)/'embedded.wprp';profile.write_text(xml)
    check=subprocess.run(['wpr.exe','-profiles',str(profile)],capture_output=True,text=True)
    assert check.returncode==0 and 'UAVFCPUSample' in check.stdout, check.stderr
print('CPU_PROFILE_VALIDATION_PASS: actual embedded XML, 4MiB memory, code sampling only, own instance, host WPR parser; no trace started')
