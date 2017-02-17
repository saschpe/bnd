import aQute.bnd.build.*;
import aQute.bnd.build.model.*;
import aQute.bnd.build.model.clauses.*;
import aQute.bnd.osgi.*;
import aQute.bnd.properties.*;
import aQute.lib.io.*;
import java.io.*;
import java.util.jar.*;

println "basedir ${basedir}"

// Check the bndrun file exist!
File bndrunFile = new File(new File(basedir, 'test-with-resolve'), "test.bndrun")
assert bndrunFile.isFile()

// Load the BndEditModel of the bndrun file so we can inspect the result
Processor processor = new Processor()
processor.setProperties(bndrunFile)
BndEditModel bem = new BndEditModel(Workspace.createStandaloneWorkspace(processor, bndrunFile.toURI()))
Document doc = new Document(IO.collect(bndrunFile))
bem.loadFrom(doc)

// Get the -runbundles.
def bemRunBundles = bem.getRunBundles()
assert null == bemRunBundles

assert new File("${basedir}/build.log").exists();

List<String> fileContents = new ArrayList<String>();

BufferedReader reader = new File("${basedir}/build.log").newReader();

String s = null;

while((s = reader.readLine()) != null) {
    fileContents.add(s);
}

// Simple test
int idx = fileContents.indexOf("Tests run  : 1");
assert idx != -1;
assert fileContents.get(idx + 1).equals("Passed     : 1");

// Resolving test
idx = fileContents.indexOf("Tests run  : 2");
assert idx != -1;
assert fileContents.get(idx + 1).equals("Passed     : 2");
