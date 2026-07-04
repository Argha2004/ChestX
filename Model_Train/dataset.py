#=============================================================
# Dataset.py   //This Dataset Script Only for Config 1 Training Script
# Before Running Train Script-1 Run This Dataset.py Script.
#=============================================================




import os
import glob
import pandas as pd
import torch
from torch.utils.data import Dataset
from PIL import Image


class ChestXrayDataset(Dataset):

    def __init__(
        self,
        csv_file,
        root_dir,
        file_list,
        transform=None
    ):

        self.transform = transform
        self.root_dir = root_dir

        print("Loading CSV...")
        self.data = pd.read_csv(csv_file)

        print("Loading image list...")
        with open(file_list, "r") as f:
            self.image_names = [
                line.strip()
                for line in f.readlines()
            ]

        print("Finding image files...")

        image_paths = glob.glob(
            os.path.join(root_dir, "**", "*.png"),
            recursive=True
        )

        self.image_dict = {
            os.path.basename(path): path
            for path in image_paths
        }

        print(
            f"Total images found: {len(self.image_dict)}"
        )

        self.labels_list = [
            "Atelectasis",
            "Consolidation",
            "Infiltration",
            "Pneumothorax",
            "Edema",
            "Emphysema",
            "Fibrosis",
            "Effusion",
            "Pneumonia",
            "Pleural_Thickening",
            "Cardiomegaly",
            "Nodule",
            "Mass",
            "Hernia"
        ]

        self.label_to_idx = {
            label: idx
            for idx, label
            in enumerate(self.labels_list)
        }

        print("Building label lookup table...")

        self.label_map = {
            row["Image Index"]:
            row["Finding Labels"]
            for _, row
            in self.data.iterrows()
        }

        print("Encoding labels...")

        self.encoded_labels = {}

        for img_name, labels in self.label_map.items():

            label_vec = torch.zeros(
                len(self.labels_list),
                dtype=torch.float32
            )

            for disease in labels.split("|"):

                if disease in self.label_to_idx:

                    label_vec[
                        self.label_to_idx[disease]
                    ] = 1.0

            self.encoded_labels[
                img_name
            ] = label_vec

        print("Dataset ready!")

    def __len__(self):

        return len(
            self.image_names
        )

    def __getitem__(
        self,
        idx
    ):

        img_name = self.image_names[idx]

        img_path = self.image_dict.get(
            img_name
        )

        if img_path is None:

            raise FileNotFoundError(
                f"Image not found: {img_name}"
            )

        try:

            with Image.open(img_path) as img:

                image = img.convert(
                    "RGB"
                )

        except Exception:

            image = Image.new(
                "RGB",
                (224, 224)
            )

        if self.transform:

            image = self.transform(
                image
            )

        label_vec = self.encoded_labels[
            img_name
        ]

        return image, label_vec


if __name__ == "__main__":

    from torchvision import transforms

    DATASET_ROOT = "/kaggle/input/datasets/organizations/nih-chest-xrays/data"

    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor()
    ])

    dataset = ChestXrayDataset(
        csv_file=f"{DATASET_ROOT}/Data_Entry_2017.csv",
        root_dir=DATASET_ROOT,
        file_list=f"{DATASET_ROOT}/train_val_list.txt",
        transform=transform
    )

    print(
        "Dataset size:",
        len(dataset)
    )

    image, label = dataset[0]

    print(
        "Image shape:",
        image.shape
    )

    print(
        "Label vector:",
        label
    )