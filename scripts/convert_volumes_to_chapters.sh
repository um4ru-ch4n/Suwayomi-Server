#!/bin/bash

# Advanced script to convert volume-based chapter numbering to sequential chapter numbering
# Format: "Chapter N - Title" where N is calculated from volume and chapter numbers
# Supports both Russian (Том/Глава) and English (Volume/Chapter) formats
# Handles missing volumes by asking user for starting chapter numbers
# Usage: ./convert_volumes_to_chapters_advanced.sh [path_to_manga_folder]

# Get the target directory from command line argument or use current directory
TARGET_DIR="${1:-.}"

# Check if the directory exists
if [ ! -d "$TARGET_DIR" ]; then
    echo "Error: Directory '$TARGET_DIR' does not exist!"
    echo "Usage: $0 [path_to_manga_folder]"
    echo "Example: $0 /path/to/manga"
    echo "Example: $0 . (for current directory)"
    exit 1
fi

echo "Starting advanced volume-to-chapter conversion process..."
echo "Target directory: $TARGET_DIR"
echo "=============================================="

# Change to target directory
cd "$TARGET_DIR" || {
    echo "Error: Cannot access directory '$TARGET_DIR'"
    exit 1
}

# Arrays to store volume information
declare -A volume_last_chapter  # volume_number -> last_chapter_number
declare -A volume_chapter_count # volume_number -> chapter_count

# First pass: analyze all folders to determine volume structure
echo "Phase 1: Analyzing volume structure..."

for folder in *; do
    if [ -d "$folder" ]; then
        echo "Analyzing: $folder"
        
        # Try Russian format: Том X ... Глава Y
        if [[ $folder =~ Том\ ([0-9]+).*Глава\ ([0-9]+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            echo "  Found Russian format: Volume $volume_num, Chapter $chapter_num"
        # Try English format: Volume X ... Chapter Y  
        elif [[ $folder =~ Volume\ ([0-9]+).*Chapter\ ([0-9]+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            echo "  Found English format: Volume $volume_num, Chapter $chapter_num"
        # Try abbreviated English format: Vol. X ... Ch. Y
        elif [[ $folder =~ Vol\.?\s*([0-9]+).*Ch\.?\s*([0-9]+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            echo "  Found abbreviated English format: Volume $volume_num, Chapter $chapter_num"
        else
            echo "  Skipping: No volume/chapter pattern found"
            continue
        fi
        
        # Track the highest chapter number for each volume
        if [[ -z "${volume_last_chapter[$volume_num]}" ]] || [[ $chapter_num -gt ${volume_last_chapter[$volume_num]} ]]; then
            volume_last_chapter[$volume_num]=$chapter_num
        fi
        
        # Count chapters per volume
        if [[ -z "${volume_chapter_count[$volume_num]}" ]]; then
            volume_chapter_count[$volume_num]=0
        fi
        ((volume_chapter_count[$volume_num]++))
    fi
done

echo ""
echo "Volume analysis results:"
echo "========================"

# Get the minimum and maximum volume numbers
min_volume=$(printf '%s\n' "${!volume_last_chapter[@]}" | sort -n | head -1)
max_volume=$(printf '%s\n' "${!volume_last_chapter[@]}" | sort -n | tail -1)

echo "Volume range: $min_volume to $max_volume"

# Check for missing volumes and ask user for starting chapter numbers
declare -A volume_start_chapter
current_chapter=1

echo ""
echo "Checking for missing volumes and determining starting chapter numbers..."
echo "======================================================================"

# Process volumes in order
for volume_num in $(printf '%s\n' "${!volume_last_chapter[@]}" | sort -n); do
    echo ""
    echo "Processing Volume $volume_num..."
    
    # Check if this volume has a gap from the previous one
    if [ "$volume_num" -gt 1 ]; then
        prev_volume=$((volume_num - 1))
        
        # Check if previous volume exists
        if [[ -z "${volume_last_chapter[$prev_volume]}" ]]; then
            echo "  WARNING: Volume $prev_volume is missing!"
            echo "  This creates a gap in the sequence."
        fi
    fi
    
    # Ask user for starting chapter number for this volume
    echo "  Volume $volume_num has chapters 1-${volume_last_chapter[$volume_num]} (${volume_chapter_count[$volume_num]} total)"
    
    if [ "$volume_num" -eq "$min_volume" ] && [ "$min_volume" -gt 1 ]; then
        echo "  This is the first available volume, but Volume 1 is missing."
        echo "  You need to specify the starting chapter number for this volume."
    elif [ "$volume_num" -gt 1 ] && [[ -z "${volume_last_chapter[$((volume_num - 1))]}" ]]; then
        echo "  The previous volume is missing, so you need to specify the starting chapter number."
    else
        echo "  Suggested starting chapter: $current_chapter"
    fi
    
    # Get user input for starting chapter
    while true; do
        read -p "  Enter starting chapter number for Volume $volume_num (or press Enter for suggested): " user_start
        
        if [[ -z "$user_start" ]]; then
            # Use suggested number
            volume_start_chapter[$volume_num]=$current_chapter
            echo "  Using suggested starting chapter: $current_chapter"
            break
        elif [[ "$user_start" =~ ^[0-9]+$ ]]; then
            # Use user input
            volume_start_chapter[$volume_num]=$user_start
            echo "  Using custom starting chapter: $user_start"
            break
        else
            echo "  Invalid input. Please enter a number or press Enter for suggested value."
        fi
    done
    
    # Update current_chapter for next volume
    current_chapter=$((volume_start_chapter[$volume_num] + volume_last_chapter[$volume_num]))
done

echo ""
echo "Final volume mapping:"
echo "===================="

# Show final mapping
for volume_num in $(printf '%s\n' "${!volume_last_chapter[@]}" | sort -n); do
    start_chapter=${volume_start_chapter[$volume_num]}
    end_chapter=$((start_chapter + volume_last_chapter[$volume_num] - 1))
    echo "Volume $volume_num: Chapters 1-${volume_last_chapter[$volume_num]} -> Chapters $start_chapter-$end_chapter"
done

echo ""
echo "Phase 2: Converting folders..."
echo "=============================="

success_count=0
error_count=0

# Second pass: rename folders
for folder in *; do
    if [ -d "$folder" ]; then
        echo "Processing: $folder"
        
        volume_num=""
        chapter_num=""
        title=""
        
        # Try Russian format: Том X ... Глава Y - Title
        if [[ $folder =~ Том\ ([0-9]+).*Глава\ ([0-9]+)\ -\ (.+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            title="${BASH_REMATCH[3]}"
            echo "  Russian format: Volume $volume_num, Chapter $chapter_num, Title: $title"
        # Try English format: Volume X ... Chapter Y - Title
        elif [[ $folder =~ Volume\ ([0-9]+).*Chapter\ ([0-9]+)\ -\ (.+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            title="${BASH_REMATCH[3]}"
            echo "  English format: Volume $volume_num, Chapter $chapter_num, Title: $title"
        # Try abbreviated English format: Vol. X ... Ch. Y - Title
        elif [[ $folder =~ Vol\.?\s*([0-9]+).*Ch\.?\s*([0-9]+)\ -\ (.+) ]]; then
            volume_num="${BASH_REMATCH[1]}"
            chapter_num="${BASH_REMATCH[2]}"
            title="${BASH_REMATCH[3]}"
            echo "  Abbreviated English format: Volume $volume_num, Chapter $chapter_num, Title: $title"
        else
            echo "  Skipping: No recognized volume/chapter pattern"
            continue
        fi
        
        if [[ -n "$volume_num" && -n "$chapter_num" && -n "$title" ]]; then
            # Calculate new sequential chapter number
            volume_start=${volume_start_chapter[$volume_num]}
            new_chapter_num=$((volume_start + chapter_num - 1))
            
            # Format with leading zeros (3 digits)
            formatted_number=$(printf "%03d" "$new_chapter_num")
            new_name="Chapter $formatted_number - $title"
            
            echo "  Converting: Volume $volume_num, Chapter $chapter_num -> Chapter $formatted_number"
            echo "  New name: $new_name"
            
            # Rename the folder
            if mv "$folder" "$new_name" 2>/dev/null; then
                echo "  SUCCESS: Renamed to '$new_name'"
                ((success_count++))
            else
                echo "  ERROR: Failed to rename '$folder'"
                ((error_count++))
            fi
        else
            echo "  ERROR: Could not extract volume/chapter/title from: $folder"
            ((error_count++))
        fi
        echo ""
    fi
done

echo "=========================================="
echo "Conversion completed!"
echo "Successfully converted: $success_count folders"
echo "Errors: $error_count folders"
echo ""
echo "Advanced features used:"
echo "- Detected missing volumes in the sequence"
echo "- Asked for custom starting chapter numbers"
echo "- Handled gaps in volume numbering"
echo ""
echo "New chapter numbering:"
echo "- Each volume's chapters are numbered sequentially based on your input"
echo "- Missing volumes are accounted for with custom starting numbers"
