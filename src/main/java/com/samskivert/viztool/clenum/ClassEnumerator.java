//
// $Id: ClassEnumerator.java,v 1.4 2001/08/12 04:36:57 mdb Exp $
// 
// viztool - a tool for visualizing collections of java classes
// Copyright (C) 2001 Michael Bayne
// 
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the
// Free Software Foundation; either version 2.1 of the License, or (at your
// option) any later version.
// 
// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// General Public License for more details.
// 
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.samskivert.viztool.clenum;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * The class enumerator is supplied with a classpath which it decomposes
 * and enumerates over all of the classes available via those classpath
 * componenets. To do this it uses component enumerators which know how to
 * enumerate all of the classes in a directory tree, in a jar file, etc.
 * The component enumerators are structured so that new enumerators can be
 * authored for new kinds of classpath component.
 */
public class ClassEnumerator implements Iterator<String>
{
    /**
     * Constructs a class enumerator with the supplied classpath. A set of component enumerators
     * will be chosen for each element and warnings will be generated for components that cannot be
     * processed for some reason or other. Those will be available following the completion of the
     * constructor via {@link #getWarningStrings}.
     */
    public ClassEnumerator (String classpath)
    {
        // decompose the path and select enumerators for each component
        List<ComponentEnumerator> enums = new ArrayList<ComponentEnumerator>();

        StringTokenizer tok = new StringTokenizer(classpath, File.pathSeparator);
        while (tok.hasMoreTokens()) {
            String component = tok.nextToken();
            // locate an enumerator for this token
            ComponentEnumerator cenum = matchEnumerator(component);
            if (cenum == null) {
                String wmsg = "Unable to match enumerator for component '" + component + "'.";
                _warnings.add(wmsg);

            } else {
                try {
                    // construct an enumerator to enumerate this component and put it on our list
                    enums.add(cenum.enumerate(component));
                } catch (EnumerationException ee) {
                    // if there was a problem creating an enumerator for said component, create a
                    // warning to that effect
                    _warnings.add(ee.getMessage());
                }
            }
        }

        // convert our list into an array
        _enums = new ComponentEnumerator[enums.size()];
        enums.toArray(_enums);

        // scan to the first class
        scanToNextClass();
    }

    /**
     * Locates an enumerator that matches the specified component and returns the prototype
     * instance of that enumerator. Returns null if no enumerator could be matched.
     */
    protected ComponentEnumerator matchEnumerator (String component)
    {
        for (ComponentEnumerator cenum : _enumerators) {
            if (cenum.matchesComponent(component)) {
                return cenum;
            }
        }
        return null;
    }

    /**
     * Returns the warnings generated in parsing the classpath and constructing enumerators for
     * each component. For example, if a classpath component specified a directory that was
     * non-existent or inaccessible, a warning would be generated for that component. If no
     * warnings were generated, a zero length array will be returned.
     */
    public Iterable<String> getWarningStrings ()
    {
        return _warnings;
    }

    /** Used by {@link #getWarnings}. Deprecated. */
    public class Warning {
        public final String reason;
        public Warning (String reason) {
            this.reason = reason;
        }
    }

    @Deprecated
    public Warning[] getWarnings ()
    {
        Warning[] warnings = new Warning[_warnings.size()];
        for (int ii = 0, ll = _warnings.size(); ii < ll; ii++) {
            warnings[ii] = new Warning(_warnings.get(ii));
        }
        return warnings;
    }

    // from interface Iterator<String>
    public boolean hasNext ()
    {
        return (_nextClass != null);
    }

    // from interface Iterator<String>
    public String next ()
    {
        String clazz = _nextClass;
        _nextClass = null;
        scanToNextClass();
        return clazz;
    }

    // from interface Iterator<String>
    public void remove ()
    {
        // not supported
    }

    /**
     * Queues up the next enumerator in the list or clears out our enumerator reference if we have
     * no remaining enumerators.
     */
    protected void scanToNextClass ()
    {
        if (_enumidx < _enums.length) {
            // grab the current enumerator
            ComponentEnumerator cenum = _enums[_enumidx];

            // if it has more classes
            if (cenum.hasMoreClasses()) {
                // get the next one
                _nextClass = cenum.nextClass();
                return;

            } else {
                // otherwise try the next enum
                _enumidx++;
                scanToNextClass();
            }
        }
    }

    protected ComponentEnumerator[] _enums;
    protected int _enumidx;
    protected String _nextClass;
    protected List<String> _warnings = new ArrayList<String>();

    protected static List<ComponentEnumerator> _enumerators = new ArrayList<ComponentEnumerator>();
    static {
        // register our enumerators
        _enumerators.add(new ZipFileEnumerator());
        _enumerators.add(new JarFileEnumerator());
        // the directory enumerator should always be last in the list because it picks up all
        // stragglers and tries enumerating them as if they were directories
        _enumerators.add(new DirectoryEnumerator());
    }
}
