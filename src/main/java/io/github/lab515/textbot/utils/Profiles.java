package io.github.lab515.textbot.utils;

import io.github.lab515.textbot.expr.Evaluator;
import io.github.lab515.textbot.expr.Oper;

import java.util.*;

/**
 * This is the hierachy profile settings, which support syntax as below
 * profile.PROFILE_NAME=BASE_PROFILE_NAME
 * property@PROFILE_NAME.PROP_NAME=PROP_VALUE property.PROP_NAME=PROP_VALUE
 * property support +!- operators, + means overwirite as always, ! means
 * overwrite when it exists in base profile, - means remove if exist e,g
 * profile@newprofile.+propname=myvalue add new function: as below profile.xxx =
 * xxxx property@xxx.xxx = xxx alias.xxx = xxx|xxx|xxx, to make things simpler,
 * alias doesn't not allowed in the hierachy profile calculation!
 * newly redesigned since 2019
 * profile.a=b            ==> @a=b
 * property@a.c = 12      ==> @a.c = 12
 * property@a { c = 12}   ==> @a{c= 12}
 * mapping@a.b = c        ==> @a.b=$c$
 * variable@a.b= $12$     ==> @a.b=$12$
 * alias.a=b+c            ==> @a=b+c (so it's unified, still allow it)
 * added: removing a prop ==> @a{-c,b,c}
 * escaper ^ remains the same
 * @author mpeng
 */
public class Profiles {
    /**
     * expose the property handler interface for external integration
     */
    static class PropertyEvaluator extends Evaluator {
        private static LinkedHashMap<String, String> apiResults = new LinkedHashMap<>();
        public boolean resolveMode = false;

        private String checkResult(String key, String val){
            String ret = null;
            synchronized (apiResults){
                ret = apiResults.get(key);
                if(val != null)apiResults.put(key,val);
            }
            return ret;
        }

        private HashSet<String> tracks;
        private Properties props;
        private LinkedHashMap<String,String> vars;
        public PropertyEvaluator(HashSet<String> tt, Properties pp, LinkedHashMap<String,String> vv){
            tracks = tt;
            props = pp;
            vars = vv;
        }
        @Override
        protected boolean accessVariable(String varName, Oper out, Object uo) {
            out.isNum = false;
            out.Val = getVariable(props,varName,true,resolveMode,tracks,vars);
            if(out.Val == null)out.Val = "";
            return true;
        }

        @Override
        protected void processAPI(String apiName, List<Oper> paras, Oper out, Object uo) {
            out.isNum = false;
            out.Val = "";
        }

    }
    /**
     * the default conifg loaded from the base profile
     */
    private static Properties   _props = null;

    /**
     * default binding profileProps,
     */
    private static Properties   _bindingProps = null;

    /**
     * default overwrite properties
     */
    private static Properties   _ovProps = null;

    private static boolean     _defImmutable = true; // fix the capillary client + qrayclient mixed profiling issue


    public static void setDefaultImmutable(boolean val){
        if(!val || _props != null)_defImmutable = val;
    }

    // no use for profiles, only for old framework enahcnement
    //private static String       _ovPrefix = null;
    private static String checkRemoverName(String name) throws Exception{
        StringBuilder sb  =new StringBuilder();
        String[] arr = name.split(",");
        for(String ar: arr){
            ar = checkProfileName(ar,true);
            if(ar != null){
                if(sb.length() > 0)sb.append(",");
                sb.append(ar);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
    private static String checkProfileName(String name, boolean propMode) throws Exception{
        if(name == null || (name = name.trim()).length() < 1)return "";
        for(int i = 0; i < name.length();i++){
            char c = name.charAt(i);
            if(!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || (i > 0 && c >= '0' && c <= '9') || (i > 0 && c == '.' && propMode))){
                if(propMode)return null;
                throw new Exception("invalid profile name: " + name);
            }
        }

        return name;
    }

    private static String trimNormal(String str){
        if(str == null || str.length() < 1)return str;
        char[] t = str.toCharArray();
        int st = 0;
        int sz = str.length();
        while(st < sz && t[st] > 4 && t[st] <= ' ')st++;
        while(sz-- > 0 && t[sz] > 4 && t[sz] <= ' ');
        sz++;
        if(sz > st)return str.substring(st,sz);
        else return "";
    }

    // add: escape char \\ handling 2019-5-20, keep compatibility with java properties
    private static String preProcessProperties(String src) {
        if (src == null)
            return null;
        char[] cs = src.toCharArray();
        StringBuilder sb = new StringBuilder();
        char lst = ' ';
        for(int i = 0; i < cs.length;i++){
            char c = cs[i];
            if(lst == '^' || lst == '\\'){
                if(c == '{' || c == '}')sb.append(c == '{' ? '\0' : '\1');
                else if(c == '$')sb.append('\2');
                else if(c == '#')sb.append('\3');
                else if(c == '\n'){} // do nothing, connected string
                else if(lst == '\\'){ // \0 \1 will be '0'
                    if(c == 't')sb.append('\t');
                    else if(c == 'n')sb.append('\4'); // special treatment as well
                    else if(c == 'r')sb.append('\r');
                    else if(c == 'b')sb.append('\b');
                    else if(c == 'f')sb.append('\f');
                    else sb.append(c);
                }else sb.append(c);
                lst = ' '; // reset
            }else{
                if(c != '^' && c != '\\')sb.append(c);
                lst = c;
            }
        }
        if(lst == '^' || lst == '\\')sb.append(lst); // add it anyway
        src = sb.toString();
        sb.setLength(0);
        String[] arr = src.split("\n");
        String line = null;
        String prefix = null;
        String singlePrefix = null;

        String last = "";

        for (int i = 0; i < arr.length; i++) {
            line = trimNormal(arr[i]);
            int pos = line.indexOf('#');
            if(pos == 0)continue;
            else if (pos > 0){
                line = trimNormal(line.substring(0, pos)); // .trim() may remove the $# escapers
            }
            if (line.length() < 1 && (prefix != null && singlePrefix != null)) {
                sb.append("\\n");
            } else if (line.endsWith("\\")) {
                // fix: make sure not a translator
                int p = 1;
                while (line.charAt(line.length() - 1 - p) == '\\')
                    p++;
                if ((p % 2) != 0) {
                    line = line.substring(0, line.length() - 1);
                    if (i < arr.length - 1) {
                        arr[i + 1] = line + arr[i + 1];
                        line = "";
                    }
                }
            }
            if (line.length() < 1)
                continue;
            if (prefix != null) {
                if (singlePrefix == null) {
                    pos = line.indexOf('{');
                    if (pos > 0) {
                        singlePrefix = prefix;
                        prefix = trimNormal(line.substring(0, pos));
                        if (prefix.length() > 0 && prefix.endsWith("=")) {
                            sb.append(singlePrefix);
                            sb.append(prefix);
                            arr[i] = line.substring(pos + 1);
                            i--;
                            continue;
                        }
                        prefix = singlePrefix;
                        singlePrefix = null;
                    }
                }

                pos = line.indexOf('}');
                if (pos >= 0) {
                    arr[i] = line.substring(pos + 1);
                    line = trimNormal(line.substring(0, pos));
                    if (singlePrefix != null) {
                        sb.append(line);
                        sb.append("\n");
                    } else {
                        if (line.length() > 0) {
                            sb.append(prefix);
                            sb.append(line);
                            sb.append("\n");
                        }
                    }
                    prefix = singlePrefix;
                    singlePrefix = null;
                    // check if it's a {, it means it's just null
                    if (prefix != null && prefix.equalsIgnoreCase("{"))
                        prefix = null;
                    i--; // let it process rest
                } else {
                    if (singlePrefix != null) {
                        sb.append(line);
                        sb.append("\\n");
                    } else {
                        sb.append(prefix);
                        sb.append(line);
                        sb.append("\n");
                    }
                }

            } else {
                // first of all, find the { if have
                pos = line.indexOf('{');
                if (pos >= 0) {
                    prefix = trimNormal(line.substring(0, pos));
                    if (prefix.length() < 1) {
                        prefix = last;
                        if (prefix.length() > 0) {
                            sb.setLength(sb.length() - prefix.length() - 1); // remove
                            // last
                            // +
                            // \n
                        }
                    } else if (prefix.endsWith("=")) {
                        singlePrefix = "{";
                        sb.append(prefix);
                    }

                    if (singlePrefix == null && prefix.length() > 0 && !prefix.endsWith("."))
                        prefix += ".";
                    arr[i] = line.substring(pos + 1);
                    i--;
                } else {
                    sb.append(line);
                    sb.append("\n");
                    last = line;
                }
            }
        }
        src = sb.toString();
        sb.setLength(0);
        src = src.replace('\0', '{').replace('\1', '}').replace('\3','#');
        return src;
    }

    /**
     * load properties based on a name of file, will look into 3 places
     */
    private static Properties loadProperties(String content, Properties ret) {
        Properties defProfiles = ret;
        String ss = preProcessProperties(content);
        if (ss == null) {
            return null;
        }
        if (defProfiles == null) {
            defProfiles = new Properties();
        }

        // change, we do manual load for customization
        String[] arr = ss.split("\n");
        for(String ar : arr){
            ar = ar.replace('\4', '\n'); // add the \n stuff
            int p = ar.indexOf('=');
            if(p < 1)continue;
            ss = trimNormal(ar.substring(0,p));
            if(ss.length() < 1)continue;
            defProfiles.put(ss,ar.substring(p+1));
        }
        return defProfiles;
    }


    public static void setOverwrittenProps(Properties ovProps) {
        _ovProps = ovProps;
    }

    public static void switchProfile(String name) throws Exception {
        loadProfile(name, null,false, true);
    }

    public static void switchProfile(String name, boolean includeSys) throws Exception {
        loadProfile(name, null,includeSys, true);
    }


    /**
     * load profile by default, no reload, include systemprops
     *
     * @param name
     * @return
     */
    public static Properties loadProfile(String name) throws Exception {
        return loadProfile(name, null, true, false);
    }


    /**
     * Default method to load certain profile
     *
     * @param name
     * @param reloadContent
     * @param includeSysProps
     * @return
     */
    public static Properties loadProfile(String name, String reloadContent, boolean includeSysProps, boolean setAsDefault)
            throws Exception {
        Properties conf = _props;
        if (reloadContent != null){
            conf = loadProperties(reloadContent, null);
            if (setAsDefault && (_props == null || !_defImmutable)) {
                _props = conf;
            }
        }
        if (conf == null) {
            // load the default profiles again, in case any issue comes up
            return null;
        }

        Properties p1 = null;
        if (includeSysProps) {
            p1 = new Properties(System.getProperties());
        }
        HashSet<String> tracks = new HashSet<String>();
        LinkedHashMap<String,String> vars = new LinkedHashMap<String,String>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        // supported variables: now, now.year, now.weekday, now.date,now.hour,etc
        vars.put("cxt.ms", Long.toString(calendar.getTimeInMillis()));
        vars.put("cxt.year", Integer.toString(calendar.get(Calendar.YEAR)));
        vars.put("cxt.month", Integer.toString(calendar.get(Calendar.MONTH)));
        vars.put("cxt.day", Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)));
        vars.put("cxt.weekday", Integer.toString(calendar.get(Calendar.DAY_OF_WEEK) - 1)); // As SUNDAY == 1, MONDAY == 2
        vars.put("cxt.hours", Integer.toString(calendar.get(Calendar.HOUR_OF_DAY)));
        vars.put("cxt.minutes", Integer.toString(calendar.get(Calendar.MINUTE)));
        vars.put("cxt.seconds", Integer.toString(calendar.get(Calendar.SECOND)));

        p1 = loadProfileInternal(name, null, conf, p1,vars,tracks);
        // ok, handle the variable, mapping
        String key = null;
        String val = null;
        // Add: overwriteen props
        if(_ovProps != null){
            //p1.putAll(_ovProps);
            copyProps(_ovProps,p1,true);
        }

        // ADD: deferred processing, include removing
        LinkedList<String> allList = new LinkedList<>();
        for(Object s : p1.keySet()){
            key = (String)s;
            if(key.startsWith("-")){
                allList.addLast(key);
            }else if(key.startsWith("?"))
                allList.addFirst(key);
        }
        // all defered
        for(String s : allList){
            val = p1.getProperty(s);
            if(key.startsWith("-")){
                if(val != null && val.length() > 0) {
                    tracks.clear();
                    val = getVariable(p1, val, false, true, tracks, vars);
                    if(val == null || val.length() < 1)val = "false";
                }
                if(val == null || val.length() < 1 || Evaluator.getBoolean(val)) {
                    for (String a : key.substring(1).split(",")) {
                        p1.remove(val); // remove it
                        //ret.remove("?" + val);
                    }
                }
            }else {
                key = s.substring(1);
                tracks.clear();
                getVariable(p1,key,true,true,tracks,vars);
            }
            p1.remove(s);
        }

        if (setAsDefault && (conf == _props && (_bindingProps == null || !_defImmutable))) {
            if (_bindingProps == null)
                _bindingProps = new Properties();
            else _bindingProps.clear();
            // FIX, putAll doesn't work with system properties
            //_bindingProps.putAll(p1);
            copyProps(p1,_bindingProps,true);
        }

        return p1;
    }

    private static void copyProps(Properties src, Properties tar, boolean overwrite){
        Enumeration<?> en = src.propertyNames();
        while(en.hasMoreElements()){
            String n = (String)en.nextElement();
            String v = src.getProperty(n);
            if(!overwrite && tar.getProperty(n) != null)continue;
            tar.setProperty(n,v);
        }
    }

    /**
     * the actual profile loader method, recursively call, make sure the profile
     * has been loaded based on the hierachy defiition
     *
     * @param name
     * @param loaded
     * @param conf
     * @param add
     * @return
     */
    private static Properties loadProfileInternal(String name, Map<String, String> loaded, Properties conf,
                                                  Properties add, LinkedHashMap<String, String> vars,HashSet<String> tracks) throws Exception {
        // name could be a variables, or contains multiple names, or just a normal stuff
        if(name == null)name = "";
        Properties ret = null;
        if (name.length() > 0) {
            tracks.clear();
            if(name.indexOf('$') >= 0){
                if(add == null)add = new Properties();
                name = getVariable(add,name,false,false,tracks,vars);
            }

            if(name.indexOf('+') >= 0){
                String[] prfs = name.split("\\+");
                for (String prf : prfs) {
                    prf = checkProfileName(prf,false);
                    if (prf.length() > 0) {
                        ret = loadProfileInternal(prf, loaded, conf, add, vars,tracks);
                        add = ret;
                    }
                }
                if (ret == null)ret = loadProfileInternal("", loaded, conf, add,vars, tracks);
            }else {
                name = checkProfileName(name,false);
                if(loaded != null && loaded.containsKey(name)){
                    ret = add;
                    if(ret == null)ret = new Properties();
                }
            }
            if (ret != null) return ret;
        }

        ret = add;
        if (ret == null) ret = new Properties();

        boolean checkProps = false;
        String mapServer = name;
        String baseServer = mapServer.length() < 1 ? "" : conf.getProperty("@" + mapServer);
        if (baseServer != null) {
            baseServer = baseServer.trim();
            if (baseServer.equals(mapServer))baseServer = "";
        } else {
            //FIX: 2018-12-13, profile. declaration is optional now
            if (mapServer.length() > 0) {
                //throw new Exception("profile not exists: " + mapServer);
                checkProps = true;
            }
            baseServer = ""; // set to root
        }

        if (loaded == null)loaded = new LinkedHashMap<String, String>();
        loaded.put(mapServer, mapServer); // loading state marked

        if(!baseServer.equals(mapServer))ret = loadProfileInternal(baseServer, loaded, conf, ret,vars,tracks);

        String prefix = mapServer.length() > 0 ? "@" + mapServer : null;
        String key = null;
        String val = null;
        LinkedList<String> allVars = new LinkedList<>();
        int cnt = 0;
        for (Object o : conf.keySet()) {
            key = (String)o;
            if (prefix != null && key.startsWith(prefix)) { // this is
                key = key.substring(prefix.length()).trim();
                if(!key.startsWith("."))continue;
                key = key.substring(1).trim();
            }else if(prefix != null || key.startsWith("@")) continue;
            checkProps = false;
            if(key.startsWith("-")){
                // check names as well
                val = checkRemoverName(key.substring(1));
                if(val != null) {
                    allVars.addLast(val);
                    // possible variable
                    allVars.addLast((String) conf.get(o));
                }
            }else {
                key = checkProfileName(key, true);
                if (key == null) continue;
                val = (String) conf.get(o);
                if (val == null) val = "";
                if (val.indexOf('$') >= 0) {
                    if(val.startsWith("=")){
                        ret.put(key,val.substring(1));
                        ret.put("?" + key,"="); // defered
                    }else{
                        allVars.addFirst(val);
                        allVars.addFirst(key);
                        cnt+=2;
                        ret.put(key,val);
                        ret.put("?" + key, "$");
                    }
                }else {
                    // it might be a defer, but doesnt matter
                    if(val.startsWith("="))val = val.substring(1);
                    ret.put(key, val.replace('\2','$').trim());
                    ret.remove("?" + key); // remove flag
                }
            }
        }
        // check if property defined
        if(checkProps)throw new Exception("profile not exists: " + mapServer);
        // process the rest stuff, map over variable
        key = null;
        for(String s : allVars){
            cnt--;
            if(key == null){
                key = s;
                continue;
            }
            val = s;
            if(cnt < 0){
                if(val.indexOf('$') >= 0){
                    if(val.startsWith("=")){
                        // add it
                        ret.put("-" + key,val.substring(1)); // defered, might be - or normal
                        key = null;
                    }else{
                        tracks.clear();
                        val = getVariable(ret,val,false, false, tracks,vars);
                        if(val == null || val.length() < 1)val = "false";
                    }
                }else{
                    if(val.startsWith("="))val = val.substring(1).replace('\2','$').trim();
                }
                if(key != null && (val == null || val.length() < 1 || Evaluator.getBoolean(val))){
                    for(String a : key.split(",")){
                        ret.remove(val); // remove it
                        ret.remove("?" + val);
                    }
                }

            }else {
                tracks.clear();
                val = getVariable(ret, key, true,false, tracks, vars);
            }
            key = null;
        }
        allVars.clear();
        loaded.remove(mapServer);
        return ret;
    }

    private static String getVariable(Properties props, String data, boolean nameMode, boolean resolveAll, HashSet<String> tracks, LinkedHashMap<String,String> vars) {
        if (data == null)
            return "";
        data = trimNormal(data);
        if(data.length() < 1)return "";
        String val = null;
        String flag = null;
        if(nameMode){ // for map mode, we just need to figure out the data
            if (tracks.contains(data))data = "[O:" + data + "]";
            else{
                val = props.getProperty(data); //
                flag = props.getProperty("?" + data); // get the flag
                if(val == null)val = "[!:" + data + "]";
                else if(flag != null){
                    // deferred
                    if(resolveAll || flag.startsWith("$")){ // "=, $"
                        tracks.add(data);
                        val = getVariable(props, val, false,resolveAll, tracks, vars);
                        props.put(data,val);
                        props.remove("?"+data);
                    }
                    else val = "[D:" + data + "]";
                }
                data = val;
            }
        }else{
            String[] arr = ("-" + data + "-").split("\\$");
            int len = arr.length;
            if (len < 3)
                return data; // incorrect format!!
            arr[0] = arr[0].substring(1);
            arr[len - 1] = arr[len - 1].substring(0, arr[len - 1].length() - 1);
            int c = len >> 1;
            int pos = 0;
            for (int i = 0; i < c; i++) {
                val = arr[i * 2 + 1];
                arr[i * 2 + 1] = "";
                if (val.length() < 1)
                    arr[i * 2 + 1] = "$";
                else if (i * 2 + 1 == len - 1)
                    arr[i * 2 + 1] = "$" + arr[i * 2 + 1];
                else {
                    val = val.replace('\2','$').trim();
                    if (val.length() < 1)
                        continue; // no change
                    // replace the exisitng stuff with new one
                    PropertyEvaluator pe = new PropertyEvaluator(tracks, props, vars);
                    pe.resolveMode = resolveAll;
                    val = pe.evaluate(val, vars);
                    if(val == null)val = "";
                    arr[i * 2 + 1] = val;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String s : arr)
                sb.append(s);
            data = sb.toString().replace('\2','$').trim();
        }
        return data;
    }

    /**
     * get default binding property
     *
     * @param name
     * @return
     */
    public static String getProperty(String name) {
        if (name == null || _bindingProps == null)
            return null;
        return _bindingProps.getProperty(name);
    }

    /**
     * @return the default binding properties
     */
    public static Properties getProperties() {
        Properties ret = new Properties();
        if (_bindingProps != null) {
            ret.putAll(_bindingProps);
        }
        return ret;
    }

    /**
     * set the default binding property
     *
     * @param name
     * @param value
     */
    public static void setProperty(String name, String value) {
        if (name == null || _bindingProps == null)
            return;
        _bindingProps.setProperty(name, value);
    }

    public static void main(String[] args) throws Exception {
        String profileContent =  "a=12\n" +
                "b= =12\n" +
                "c=12^{\n" +
                "aa==$b+a$\n" +
                //"@b=\n" +
                "@b{\n" +
                "  a=$b$^$\n" +
                "}\n" +
                "@b.-a";

        Properties ss = Profiles.loadProfile("b", profileContent, false, true);

        for(Object s : ss.keySet()){
            System.out.println(s + ":" + ss.get(s));
        }
    }
}
