#!/usr/bin/env bash
# Memory sampler for the `check-*` jobs (#423). Appends one compact line every <interval> seconds
# to <log-file>:
#
#   [mem] 05:18:30 used=14548M avail=1433M build=13204M gradle=5492M kotlin@2.4.10=4228M ...
#
# `used`/`avail` are system-wide (free -m). `build` sums the RSS of every process started after
# the sampler, which on a runner is the build (and locally excludes other builds on the host).
# The individual entries are the largest of those processes, JVMs named by role. The first time a
# Kotlin daemon is seen it gets its own line with its compiler version and -Xmx, because more than
# one daemon at a time is the thing to explain.
#
# A lost runner uploads nothing, not even its job log, so this only shows how close a run that
# finished came to the limit.
#
# usage: mem-sampler.sh <interval-seconds> <log-file>
interval=${1:-30}
log=${2:?usage: mem-sampler.sh <interval-seconds> <log-file>}
since=$(date +%s)
declare -A seen

emit() { echo "$1" >> "$log"; }

while :; do
    now=$(date +%s)
    read -r used avail < <(free -m | awk '/^Mem:/ { print $3, $7 }')
    total=0
    top=()
    # pid, RSS in KiB, seconds since start, command name; largest first.
    while read -r pid rss age comm; do
        [ $((now - age)) -ge "$since" ] || continue
        total=$((total + rss))
        [ ${#top[@]} -lt 6 ] || continue
        name=$comm
        if [ "$comm" = java ]; then
            args=$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null)
            case $args in
                *KotlinCompileDaemon*)
                    version=$(grep -oE 'kotlin-(compiler-embeddable|build-tools-impl)[-/]([0-9][^/ :]*)' <<< "$args" |
                        head -1 | grep -oE '[0-9][^/ :]*$')
                    name="kotlin@${version:-?}"
                    if [ -z "${seen[$pid]}" ]; then
                        seen[$pid]=1
                        xmx=$(grep -oE -- '-Xmx[^ ]+' <<< "$args" | tail -1)
                        emit "[mem] new Kotlin daemon pid=$pid compiler=${version:-?} ${xmx:--Xmx(default)}"
                    fi
                    ;;
                *GradleDaemon*) name=gradle ;;
                *GradleWorkerMain*) name=worker ;;
            esac
        fi
        top+=("$name=$((rss / 1024))M")
    done < <(ps -eo pid=,rss=,etimes=,comm= --sort=-rss)
    emit "[mem] $(date -u +%T) used=${used}M avail=${avail}M build=$((total / 1024))M ${top[*]}"
    sleep "$interval"
done
