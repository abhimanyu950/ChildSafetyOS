# Script to generate Android launcher icons from ic_launcher-playstore.png
# Requires Pillow: pip install Pillow

from PIL import Image
import os

# Source image path
source_path = r"app\src\main\ic_launcher-playstore.png"
res_dir = r"app\src\main\res"

# Android mipmap densities and their sizes
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Open the source image
img = Image.open(source_path)
print(f"Loaded source image: {source_path} ({img.size[0]}x{img.size[1]})")

# Generate icons for each density
for folder, size in densities.items():
    target_folder = os.path.join(res_dir, folder)
    os.makedirs(target_folder, exist_ok=True)
    
    # Resize with high-quality resampling
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # Save as WebP (Android preferred format)
    output_path = os.path.join(target_folder, "ic_launcher.webp")
    resized.save(output_path, "WEBP", quality=95)
    print(f"Created: {output_path} ({size}x{size})")
    
    # Also create round version (same image, Android masks it)
    round_path = os.path.join(target_folder, "ic_launcher_round.webp")
    resized.save(round_path, "WEBP", quality=95)
    print(f"Created: {round_path} ({size}x{size})")
    
    # Create foreground for adaptive icon (larger, 108dp with 72dp safe zone)
    # The foreground should be 1.5x the launcher size to fill the 108dp canvas
    fg_size = int(size * 1.5)  # This gives the full adaptive icon size
    fg_resized = img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
    # Create a transparent canvas of the correct adaptive icon size
    canvas_size = int(size * 108 / 48)  # Scale to 108dp equivalent
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    # Center the icon on the canvas
    offset = (canvas_size - fg_size) // 2
    canvas.paste(fg_resized, (offset, offset), fg_resized if fg_resized.mode == 'RGBA' else None)
    
    fg_path = os.path.join(target_folder, "ic_launcher_foreground.webp")
    canvas.save(fg_path, "WEBP", quality=95)
    print(f"Created: {fg_path} ({canvas_size}x{canvas_size})")

print("\nDone! Icon generation complete.")
