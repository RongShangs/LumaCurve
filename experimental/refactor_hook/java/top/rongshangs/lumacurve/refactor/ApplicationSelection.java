package top.rongshangs.lumacurve.refactor;

import java.util.*;

/** Shared list semantics: bounded, additive category selection and stable unknown entries. */
final class ApplicationSelection {
    static final int LIMIT=64;
    static LinkedHashSet<String> read(Collection<String> values){
        LinkedHashSet<String> out=new LinkedHashSet<>();if(values==null)return out;
        if(values.size()>LIMIT)throw new IllegalArgumentException("应用名单最多 64 项");
        for(String value:values){if(!BrightnessControlOptions.validPackage(value))throw new IllegalArgumentException("应用包名无效");out.add(value);}return out;
    }
    static int add(Set<String> selected,Collection<String> candidates){
        int added=0;for(String pkg:candidates)if(BrightnessControlOptions.validPackage(pkg)&&!selected.contains(pkg)&&selected.size()<LIMIT){selected.add(pkg);added++;}return added;
    }
    static boolean fits(Set<String> selected,Collection<String> others,String pkg){Set<String> all=new HashSet<>(others);all.addAll(selected);all.add(pkg);return all.size()<=LIMIT;}
    static int add(Set<String> selected,Collection<String> candidates,Collection<String> others){int n=0;for(String pkg:candidates)if(BrightnessControlOptions.validPackage(pkg)&&!selected.contains(pkg)&&fits(selected,others,pkg)){selected.add(pkg);n++;}return n;}
    /** Planning is read-only. Only an explicit confirmation commits cross-list moves. */
    static final class Change {
        final Set<String> before,after,conflicts,priorOthers;
        Change(Set<String> selected,Collection<String> candidates,Set<String> others){before=new LinkedHashSet<>(selected);after=new LinkedHashSet<>(selected);priorOthers=new LinkedHashSet<>(others);add(after,candidates,others);conflicts=new LinkedHashSet<>(after);conflicts.removeAll(before);conflicts.retainAll(others);}
        int added(){return after.size()-before.size();}
        void apply(Set<String> selected,Set<String> others,Set<String> moved){if(!selected.equals(before)||!others.equals(priorOthers))throw new IllegalStateException("名单已变化，请重新选择");selected.clear();selected.addAll(after);others.removeAll(conflicts);moved.addAll(conflicts);}
    }
    static boolean matches(String name,String pkg,String query){String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);return q.isEmpty()||(name+" "+pkg).toLowerCase(Locale.ROOT).contains(q);}
}
